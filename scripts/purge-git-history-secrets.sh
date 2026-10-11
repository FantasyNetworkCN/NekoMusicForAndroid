#!/usr/bin/env bash
# 从 Git 全部历史（所有分支 + 所有标签）中移除/脱敏泄露的机密。
#
# 设计原则：
#   1. 只读源仓库，清理动作在临时 mirror 克隆里执行，校验通过后由人工强制推送。
#   2. 脚本内不内置任何机密默认值——机密必须显式传入，避免被再次提交进仓库。
#
# 用法：
#   scripts/purge-git-history-secrets.sh --secret <机密> [选项] [源仓库路径]
#   scripts/purge-git-history-secrets.sh --secret-file <文件> [选项] [源仓库路径]
#
# 选项：
#   --path <相对路径>     从全部历史中彻底删除该文件（可重复，默认 keystore.properties）
#   --secret <字符串>     待脱敏并在历史中校验的机密（可重复；更推荐下面的文件方式）
#   --secret-file <文件>  逐行读取待脱敏机密（避免口令进入 shell 历史与进程列表）
#   --allow-slow          无 git-filter-repo 时，允许用 filter-branch 做全量文本脱敏（很慢）
#
# 注意：清理完成后请销毁 --secret-file 文件，并确认推送前没有任何文件重新包含该机密。
set -euo pipefail

PATHS=()
SECRETS=()
ALLOW_SLOW=0
SOURCE_REPO=""

while [ $# -gt 0 ]; do
  case "$1" in
    --path) PATHS+=("$2"); shift 2 ;;
    --secret) SECRETS+=("$2"); shift 2 ;;
    --secret-file)
      [ -r "$2" ] || { echo "无法读取机密文件: $2" >&2; exit 2; }
      while IFS= read -r line; do
        [ -n "$line" ] && SECRETS+=("$line")
      done < "$2"
      shift 2 ;;
    --allow-slow) ALLOW_SLOW=1; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    -*) echo "未知选项: $1" >&2; exit 2 ;;
    *) SOURCE_REPO="$1"; shift ;;
  esac
done

[ ${#PATHS[@]} -eq 0 ] && PATHS=("keystore.properties")

if [ ${#SECRETS[@]} -eq 0 ]; then
  echo "错误：必须通过 --secret 或 --secret-file 指定待脱敏机密（脚本不内置默认值）。" >&2
  exit 2
fi

[ -z "$SOURCE_REPO" ] && SOURCE_REPO="$(git rev-parse --show-toplevel)"

workdir="$(mktemp -d)"
mirror="${workdir}/mirror.git"

echo "源仓库  : ${SOURCE_REPO}"
echo "删除路径: ${PATHS[*]}"
echo "脱敏机密: ${#SECRETS[@]} 条（内容不回显）"
echo "临时镜像: ${mirror}"
echo

git clone --quiet --mirror "${SOURCE_REPO}" "${mirror}"
cd "${mirror}"

echo "清理前：$(git rev-list --all --count) 个提交 / $(git tag | wc -l) 个标签"
echo

FR_CMD=()
if command -v git-filter-repo >/dev/null 2>&1; then
  FR_CMD=(git-filter-repo)
elif [ -n "${GIT_FILTER_REPO:-}" ] && [ -f "${GIT_FILTER_REPO}" ]; then
  FR_CMD=(python3 "${GIT_FILTER_REPO}")
elif [ -f /tmp/neko-tools/git-filter-repo ]; then
  FR_CMD=(python3 /tmp/neko-tools/git-filter-repo)
fi

if [ ${#FR_CMD[@]} -gt 0 ]; then
  echo "使用 git-filter-repo"
  fr_args=(--force)
  for p in "${PATHS[@]}"; do fr_args+=(--path "$p"); done
  fr_args+=(--invert-paths)
  repl="$(mktemp)"
  chmod 600 "${repl}"
  for s in "${SECRETS[@]}"; do printf '%s==>REDACTED-SECRET\n' "$s" >> "${repl}"; done
  fr_args+=(--replace-text "${repl}")
  "${FR_CMD[@]}" "${fr_args[@]}"
  rm -f "${repl}"
else
  echo "未找到 git-filter-repo，回退到 git filter-branch（仅支持 --path 删除）"
  rm_args=""
  for p in "${PATHS[@]}"; do rm_args="${rm_args} '${p}'"; done
  FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f \
    --index-filter "git rm --cached --ignore-unmatch${rm_args}" \
    --prune-empty \
    --tag-name-filter cat \
    -- --all
  rm -rf refs/original
  git reflog expire --expire=now --all
  git gc --prune=now --quiet

  if [ "${ALLOW_SLOW}" -eq 1 ]; then
    echo "使用 tree-filter 做文本脱敏（较慢）..."
    sed_expr=""
    for s in "${SECRETS[@]}"; do sed_expr="${sed_expr} -e s/${s}/REDACTED-SECRET/g"; done
    FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f \
      --tree-filter "grep -rIl --exclude-dir=.git -F -e '${SECRETS[0]}' . 2>/dev/null | xargs -r sed -i${sed_expr}" \
      --tag-name-filter cat \
      -- --all
    rm -rf refs/original
    git reflog expire --expire=now --all
    git gc --prune=now --quiet
  else
    echo "警告：未做文本脱敏（需要 git-filter-repo，或加 --allow-slow 用 filter-branch 慢速重写）"
  fi
fi

echo
echo "==== 校验 ===="
failed=0

for p in "${PATHS[@]}"; do
  if git log --all --pretty=format: --name-only | sort -u | grep -qx "$p"; then
    echo "FAIL: 历史中仍存在 ${p}"; failed=1
  else
    echo "OK  : 历史中不再存在 ${p}"
  fi
done

i=0
for s in "${SECRETS[@]}"; do
  i=$((i + 1))
  if git log --all -S"$s" --oneline | grep -q .; then
    echo "FAIL: 历史中仍可通过 -S 命中第 ${i} 条机密"; failed=1
  else
    echo "OK  : 第 ${i} 条机密的全历史命中数为 0"
  fi
done

if git log --all --pretty=format: --name-only | sort -u | grep -qiE '\.(jks|keystore|p12|pfx)$'; then
  echo "FAIL: 历史中仍存在密钥库二进制文件"; failed=1
else
  echo "OK  : 历史中不再存在密钥库二进制文件"
fi

echo
echo "清理后：$(git rev-list --all --count) 个提交 / $(git tag | wc -l) 个标签"

if [ "${failed}" -ne 0 ]; then
  echo
  echo "校验未通过，请勿推送。临时目录保留：${workdir}"
  exit 1
fi

REMOTE_URL="$(git -C "${SOURCE_REPO}" remote get-url origin 2>/dev/null || echo '')"
case "${REMOTE_URL}" in
  ''|/*|./*|../*) REMOTE_URL="<在此填入远端 URL>" ;;
esac

cat <<EOF

==== 校验通过，下一步（需人工确认后执行） ====

cd ${mirror}
git remote remove origin 2>/dev/null || true
git remote add origin ${REMOTE_URL}
# 不要用 --mirror：GitHub 会公布 refs/pull/*，--mirror 会尝试删除它们并可能损坏 PR。
git push --force origin 'refs/heads/*:refs/heads/*'
git push --force origin 'refs/tags/*:refs/tags/*'

推送后：
  1. 通知所有协作者删除本地克隆并重新 clone（旧历史已不可 fast-forward 合入）。
  2. GitHub 上旧提交仍可通过 refs/pull/* 与「按 SHA 直取」访问，需联系 GitHub Support 清理缓存。
  3. 只读复验：重新 clone 后 git log --all --pretty=format: --name-only | grep -x '${PATHS[0]}'  应为空

临时目录（含清理后的历史）：${workdir}
EOF
