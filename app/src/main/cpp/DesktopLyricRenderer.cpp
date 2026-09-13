// 桌面歌词解析 / VR HUD 数据渲染（JNI 实现）。
//
// 说明：这里刻意使用纯 C 实现（仅依赖 libc + liblog），不使用 std::regex /
// std::stringstream 等 libc++ 重型组件。此前用 std::regex 会连带把 locale
// （moneypunct / time_get / ctype）、iostream 与 C++ 异常展开表静态链进来，
// 使产物 .so 超过 1 MiB（占整包约 18%）；改为手写解析与 JSON 拼接后，
// 链接 libc++ 的符号为零，体积降到几十 KB。对外 JNI 契约保持不变。

#include <jni.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "DesktopLyricRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef struct {
    float time;          // 时间（秒）
    char* text;          // 歌词文本
    char* translation;   // 翻译文本
} LyricLine;

typedef struct {
    LyricLine* lines;
    size_t count;
    size_t capacity;
    int currentMusicId;
    int isInitialized;
} DesktopLyricRenderer;

/* ============================ 小工具 ============================ */

static char* dup_range(const char* s, size_t len) {
    char* out = (char*) malloc(len + 1);
    if (out == NULL) {
        return NULL;
    }
    if (len > 0) {
        memcpy(out, s, len);
    }
    out[len] = '\0';
    return out;
}

static int is_blank_char(char c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
}

/** 等价于 std::string 的 find_first_not_of / find_last_not_of(" \t\r\n") 后裁剪。 */
static char* trim_dup(const char* s, size_t len) {
    size_t start = 0;
    while (start < len && is_blank_char(s[start])) {
        start++;
    }
    size_t end = len;
    while (end > start && is_blank_char(s[end - 1])) {
        end--;
    }
    return dup_range(s + start, end - start);
}

static int is_ascii_digit(char c) {
    return c >= '0' && c <= '9';
}

/**
 * 在 line 中查找 LRC 时间戳 `[mm:ss.xxx]`（毫秒支持 1~5 位），
 * 语义与原先的 std::regex("\\[(\\d{2}):(\\d{2})\\.(\\d{1,5})\\]") + regex_search 一致。
 *
 * @return 匹配起点（找不到返回 -1），并通过出参返回解析结果与匹配长度
 */
static long find_timestamp(const char* line, size_t len, int* minutes, int* seconds,
                           double* millis, size_t* matchLen) {
    for (size_t i = 0; i + 7 <= len; i++) {
        if (line[i] != '[') {
            continue;
        }
        if (!is_ascii_digit(line[i + 1]) || !is_ascii_digit(line[i + 2]) || line[i + 3] != ':') {
            continue;
        }
        if (!is_ascii_digit(line[i + 4]) || !is_ascii_digit(line[i + 5]) || line[i + 6] != '.') {
            continue;
        }
        size_t digits = 0;
        double value = 0;
        size_t j = i + 7;
        while (j < len && digits < 5 && is_ascii_digit(line[j])) {
            value = value * 10 + (line[j] - '0');
            digits++;
            j++;
        }
        if (digits == 0 || j >= len || line[j] != ']') {
            continue;
        }
        *minutes = (line[i + 1] - '0') * 10 + (line[i + 2] - '0');
        *seconds = (line[i + 4] - '0') * 10 + (line[i + 5] - '0');
        *millis = value;
        *matchLen = j - i + 1;
        return (long) i;
    }
    return -1;
}

/* ============================ 动态缓冲区 ============================ */

typedef struct {
    char* data;
    size_t len;
    size_t cap;
} Buf;

static void buf_init(Buf* b) {
    b->data = NULL;
    b->len = 0;
    b->cap = 0;
}

static void buf_append(Buf* b, const char* s, size_t n) {
    if (b->len + n + 1 > b->cap) {
        size_t newCap = b->cap ? b->cap : 128;
        while (newCap < b->len + n + 1) {
            newCap *= 2;
        }
        char* tmp = (char*) realloc(b->data, newCap);
        if (tmp == NULL) {
            return;
        }
        b->data = tmp;
        b->cap = newCap;
    }
    memcpy(b->data + b->len, s, n);
    b->len += n;
    b->data[b->len] = '\0';
}

static void buf_append_cstr(Buf* b, const char* s) {
    buf_append(b, s, strlen(s));
}

static void buf_appendf(Buf* b, const char* fmt, ...) {
    char tmp[64];
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(tmp, sizeof(tmp), fmt, ap);
    va_end(ap);
    if (n > 0) {
        buf_append(b, tmp, (size_t) n);
    }
}

/** 与原先 escapeJson 行为一致（控制字符转 \uXXXX，高位字节原样保留）。 */
static void buf_append_escaped(Buf* b, const char* in) {
    for (const char* p = in; *p != '\0'; p++) {
        char c = *p;
        switch (c) {
            case '"': buf_append_cstr(b, "\\\""); break;
            case '\\': buf_append_cstr(b, "\\\\"); break;
            case '\b': buf_append_cstr(b, "\\b"); break;
            case '\f': buf_append_cstr(b, "\\f"); break;
            case '\n': buf_append_cstr(b, "\\n"); break;
            case '\r': buf_append_cstr(b, "\\r"); break;
            case '\t': buf_append_cstr(b, "\\t"); break;
            default:
                if ((unsigned char) c < ' ') {
                    char esc[7];
                    snprintf(esc, sizeof(esc), "\\u%04X", (unsigned char) c);
                    buf_append(b, esc, 6);
                } else {
                    buf_append(b, &c, 1);
                }
        }
    }
}

/* ============================ 歌词解析 ============================ */

typedef struct {
    size_t start;
    size_t len;
} Slice;

/** 按 '\n' 切分，语义与不断调用 std::getline(stream, line) 一致（保留行内 '\r'）。 */
static Slice* split_lines(const char* s, size_t len, size_t* outCount) {
    size_t cap = 16;
    size_t n = 0;
    Slice* arr = (Slice*) malloc(cap * sizeof(Slice));
    if (arr == NULL) {
        *outCount = 0;
        return NULL;
    }
    size_t start = 0;
    for (size_t i = 0; i <= len; i++) {
        if (i != len && s[i] != '\n') {
            continue;
        }
        if (i == len && i == start) {
            break; // 结尾换行不再产生空行
        }
        if (n == cap) {
            cap *= 2;
            Slice* tmp = (Slice*) realloc(arr, cap * sizeof(Slice));
            if (tmp == NULL) {
                free(arr);
                *outCount = 0;
                return NULL;
            }
            arr = tmp;
        }
        arr[n].start = start;
        arr[n].len = i - start;
        n++;
        start = i + 1;
    }
    *outCount = n;
    return arr;
}

static void free_lines(DesktopLyricRenderer* renderer) {
    for (size_t i = 0; i < renderer->count; i++) {
        free(renderer->lines[i].text);
        free(renderer->lines[i].translation);
    }
    renderer->count = 0;
}

static void append_line(DesktopLyricRenderer* renderer, float time, char* text, char* translation) {
    if (renderer->count == renderer->capacity) {
        size_t newCap = renderer->capacity ? renderer->capacity * 2 : 8;
        LyricLine* tmp = (LyricLine*) realloc(renderer->lines, newCap * sizeof(LyricLine));
        if (tmp == NULL) {
            free(text);
            free(translation);
            return;
        }
        renderer->lines = tmp;
        renderer->capacity = newCap;
    }
    LyricLine* line = &renderer->lines[renderer->count++];
    line->time = time;
    line->text = text;
    line->translation = translation;
}

static void parse_lyrics(DesktopLyricRenderer* renderer, const char* lrcText, int musicId) {
    free_lines(renderer);
    renderer->currentMusicId = musicId;

    size_t lineCount = 0;
    Slice* lines = split_lines(lrcText, strlen(lrcText), &lineCount);

    for (size_t i = 0; i < lineCount; i++) {
        const char* currentLine = lrcText + lines[i].start;
        size_t currentLen = lines[i].len;

        int minutes = 0;
        int seconds = 0;
        double millis = 0;
        size_t matchLen = 0;
        long matchPos = find_timestamp(currentLine, currentLen, &minutes, &seconds, &millis, &matchLen);
        if (matchPos < 0) {
            continue;
        }

        float time = (float) minutes * 60.0f + (float) seconds + (float) millis / 1000.0f;
        size_t textStart = (size_t) matchPos + matchLen;
        char* text = trim_dup(currentLine + textStart, currentLen - textStart);

        char* translation = dup_range("", 0);
        int hasTranslation = 0;
        if (i + 1 < lineCount) {
            const char* nextLine = lrcText + lines[i + 1].start;
            size_t nextLen = lines[i + 1].len;
            // 翻译行通常以 { } 包裹，且不含时间戳
            if (nextLen > 0 && nextLine[0] == '{' && nextLine[nextLen - 1] == '}') {
                if (find_timestamp(nextLine, nextLen, &minutes, &seconds, &millis, &matchLen) < 0) {
                    hasTranslation = 1;
                    size_t innerLen = nextLen - 2;
                    char* filtered = (char*) malloc(innerLen + 1);
                    if (filtered != NULL) {
                        size_t w = 0;
                        for (size_t k = 0; k < innerLen; k++) {
                            char c = nextLine[1 + k];
                            if (c == '\\' || c == '"' || c == '\'') {
                                continue;
                            }
                            filtered[w++] = c;
                        }
                        filtered[w] = '\0';
                        free(translation);
                        translation = trim_dup(filtered, w);
                        free(filtered);
                    }
                }
            }
        }

        append_line(renderer, time, text, translation);

        // 如果找到翻译行，跳过它
        if (hasTranslation) {
            i++;
        }
    }

    free(lines);
    LOGI("Parsed %zu lyrics for musicId: %d", renderer->count, musicId);
}

/** 查找最后一个时间小于等于 currentTime 的歌词行（从末尾向前扫描）。 */
static LyricLine* current_lyric(DesktopLyricRenderer* renderer, float currentTime) {
    if (renderer->count == 0) {
        return NULL;
    }
    for (size_t idx = renderer->count; idx > 0; idx--) {
        if (renderer->lines[idx - 1].time <= currentTime) {
            return &renderer->lines[idx - 1];
        }
    }
    return NULL;
}

static char* vrhud_data(DesktopLyricRenderer* renderer, float currentTime) {
    LyricLine* current = current_lyric(renderer, currentTime);
    if (current == NULL) {
        const char* empty = "{\"text\":\"暂无歌词\",\"translation\":\"\",\"hasLyric\":false}";
        return dup_range(empty, strlen(empty));
    }

    Buf buf;
    buf_init(&buf);
    buf_append_cstr(&buf, "{\"text\":\"");
    buf_append_escaped(&buf, current->text);
    buf_append_cstr(&buf, "\",\"translation\":\"");
    buf_append_escaped(&buf, current->translation);
    buf_append_cstr(&buf, "\",\"time\":");
    buf_appendf(&buf, "%.6g", (double) current->time);
    buf_append_cstr(&buf, ",\"currentTime\":");
    buf_appendf(&buf, "%.6g", (double) currentTime);
    buf_append_cstr(&buf, ",\"hasLyric\":true}");
    return buf.data;
}

static char* vrhud_context(DesktopLyricRenderer* renderer, float currentTime, int contextLines) {
    if (renderer->count == 0) {
        return dup_range("[]", 2);
    }

    int currentIndex = -1;
    for (size_t i = 0; i < renderer->count; i++) {
        if (renderer->lines[i].time <= currentTime) {
            currentIndex = (int) i;
        } else {
            break;
        }
    }
    if (currentIndex < 0) {
        currentIndex = 0;
    }

    int startIdx = currentIndex - contextLines;
    if (startIdx < 0) {
        startIdx = 0;
    }
    int endIdx = currentIndex + contextLines;
    if (endIdx > (int) renderer->count - 1) {
        endIdx = (int) renderer->count - 1;
    }

    Buf buf;
    buf_init(&buf);
    buf_append_cstr(&buf, "[");
    for (int i = startIdx; i <= endIdx; i++) {
        if (i > startIdx) {
            buf_append_cstr(&buf, ",");
        }
        buf_append_cstr(&buf, "{\"text\":\"");
        buf_append_escaped(&buf, renderer->lines[i].text);
        buf_append_cstr(&buf, "\",\"translation\":\"");
        buf_append_escaped(&buf, renderer->lines[i].translation);
        buf_append_cstr(&buf, "\",\"time\":");
        buf_appendf(&buf, "%.6g", (double) renderer->lines[i].time);
        buf_append_cstr(&buf, ",\"isCurrent\":");
        buf_append_cstr(&buf, (i == currentIndex) ? "true" : "false");
        buf_append_cstr(&buf, "}");
    }
    buf_append_cstr(&buf, "]");
    return buf.data;
}

/* ============================ 全局实例 ============================ */

static DesktopLyricRenderer* g_renderer = NULL;

static DesktopLyricRenderer* get_renderer(void) {
    if (g_renderer == NULL) {
        g_renderer = (DesktopLyricRenderer*) calloc(1, sizeof(DesktopLyricRenderer));
        if (g_renderer != NULL) {
            g_renderer->currentMusicId = -1;
            g_renderer->isInitialized = 0;
            LOGI("DesktopLyricRenderer created");
        }
    }
    return g_renderer;
}

/* ============================ JNI 接口 ============================ */

extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeInitialize(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    DesktopLyricRenderer* renderer = get_renderer();
    if (renderer == NULL) {
        return;
    }
    renderer->isInitialized = 1;
    LOGI("DesktopLyricRenderer initialized for VR HUD");
}

extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeParseLyrics(JNIEnv* env, jclass clazz,
                                                               jstring lrcText, jint musicId) {
    (void) clazz;
    if (lrcText == NULL) {
        LOGE("lrcText is null");
        return;
    }
    const char* lrcStr = env->GetStringUTFChars(lrcText, NULL);
    if (lrcStr == NULL) {
        LOGE("Failed to get lrcText string");
        return;
    }
    DesktopLyricRenderer* renderer = get_renderer();
    if (renderer != NULL) {
        parse_lyrics(renderer, lrcStr, (int) musicId);
    }
    env->ReleaseStringUTFChars(lrcText, lrcStr);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeGetCurrentLyric(JNIEnv* env, jclass clazz,
                                                                   jfloat currentTime) {
    (void) clazz;
    const char* fallback = "{\"text\":\"暂无歌词\",\"translation\":\"\",\"hasLyric\":false}";
    DesktopLyricRenderer* renderer = get_renderer();
    char* json = NULL;
    if (renderer == NULL || current_lyric(renderer, currentTime) == NULL) {
        json = dup_range(fallback, strlen(fallback));
    } else {
        json = vrhud_data(renderer, currentTime);
    }
    jstring result = env->NewStringUTF(json != NULL ? json : fallback);
    free(json);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeGetLyricContext(JNIEnv* env, jclass clazz,
                                                                   jfloat currentTime,
                                                                   jint contextLines) {
    (void) clazz;
    DesktopLyricRenderer* renderer = get_renderer();
    char* json = renderer != NULL
                 ? vrhud_context(renderer, currentTime, (int) contextLines)
                 : dup_range("[]", 2);
    jstring result = env->NewStringUTF(json != NULL ? json : "[]");
    free(json);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeGetCurrentMusicId(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    DesktopLyricRenderer* renderer = get_renderer();
    return renderer != NULL ? (jint) renderer->currentMusicId : -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeGetLyricCount(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    DesktopLyricRenderer* renderer = get_renderer();
    return renderer != NULL ? (jint) renderer->count : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeCleanup(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    if (g_renderer != NULL) {
        free_lines(g_renderer);
        free(g_renderer->lines);
        free(g_renderer);
        g_renderer = NULL;
        LOGI("DesktopLyricRenderer destroyed");
        LOGI("DesktopLyricRenderer cleaned up");
    }
}
