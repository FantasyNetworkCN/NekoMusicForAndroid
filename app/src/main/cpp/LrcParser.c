#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <regex.h>

// 检查LRC文件内容是否包含有效的时间戳
JNIEXPORT jboolean JNICALL
Java_com_neko_music_util_LrcParser_nativeIsValidLrcContent(JNIEnv *env, jclass type, jstring content) {
    if (content == NULL) {
        return JNI_FALSE;
    }

    const char *contentStr = (*env)->GetStringUTFChars(env, content, 0);
    if (contentStr == NULL) {
        return JNI_FALSE;
    }

    // LRC 时间戳格式：[mm:ss.xx] 或 [mm:ss.xxx]
    // mm: 分钟（00-59）
    // ss: 秒（00-59）
    // xx 或 xxx: 毫秒（00-999）
    regex_t regex;
    int ret = regcomp(&regex, "\\[[0-5][0-9]:[0-5][0-9]\\.[0-9][0-9]\\([0-9]\\)?\\]", REG_EXTENDED);
    if (ret != 0) {
        (*env)->ReleaseStringUTFChars(env, content, contentStr);
        return JNI_FALSE;
    }

    ret = regexec(&regex, contentStr, 0, NULL, 0);
    regfree(&regex);
    (*env)->ReleaseStringUTFChars(env, content, contentStr);

    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

// 解析LRC文件内容，返回前几行作为预览
JNIEXPORT jstring JNICALL
Java_com_neko_music_util_LrcParser_nativeParseLrcPreview(JNIEnv *env, jclass type, jstring content, jint maxLines) {
    if (content == NULL) {
        return (*env)->NewStringUTF(env, "");
    }

    const char *contentStr = (*env)->GetStringUTFChars(env, content, 0);
    if (contentStr == NULL) {
        return (*env)->NewStringUTF(env, "");
    }

    // 分配缓冲区存储预览内容
    int bufferSize = 1000;
    char *preview = (char *)malloc(bufferSize);
    if (preview == NULL) {
        (*env)->ReleaseStringUTFChars(env, content, contentStr);
        return (*env)->NewStringUTF(env, "");
    }

    preview[0] = '\0';
    int currentLen = 0;
    int lineCount = 0;

    // 逐行读取内容
    const char *ptr = contentStr;
    char lineBuffer[256];
    int lineBufferIndex = 0;

    while (*ptr != '\0' && lineCount < maxLines) {
        if (*ptr == '\n' || *ptr == '\r') {
            // 遇到换行符，结束当前行
            if (lineBufferIndex > 0) {
                lineBuffer[lineBufferIndex] = '\0';

                // 跳过时间戳行
                if (lineBuffer[0] == '[') {
                    // 检查是否是时间戳行，如果是则跳过
                    int skip = 1;
                    for (int i = 0; i < lineBufferIndex; i++) {
                        if (lineBuffer[i] == ']') {
                            // 找到时间戳结束位置，检查后面是否有歌词
                            if (i + 1 < lineBufferIndex && lineBuffer[i + 1] != '\0') {
                                skip = 0;
                            }
                            break;
                        }
                    }

                    if (skip) {
                        lineBufferIndex = 0;
                        ptr++;
                        continue;
                    }
                }

                // 检查缓冲区是否足够
                if (currentLen + lineBufferIndex + 2 > bufferSize) {
                    bufferSize *= 2;
                    char *newPreview = (char *)realloc(preview, bufferSize);
                    if (newPreview == NULL) {
                        free(preview);
                        (*env)->ReleaseStringUTFChars(env, content, contentStr);
                        return (*env)->NewStringUTF(env, "");
                    }
                    preview = newPreview;
                }

                // 添加换行符
                if (currentLen > 0) {
                    strcat(preview, "\n");
                    currentLen++;
                }

                // 添加行内容
                strcat(preview, lineBuffer);
                currentLen += lineBufferIndex;
                lineCount++;
            }

            lineBufferIndex = 0;
        } else {
            if (lineBufferIndex < 255) {
                lineBuffer[lineBufferIndex++] = *ptr;
            }
        }
        ptr++;
    }

    // 添加最后一行（如果没有换行符）
    if (lineBufferIndex > 0 && lineCount < maxLines) {
        lineBuffer[lineBufferIndex] = '\0';

        if (currentLen + lineBufferIndex + 1 > bufferSize) {
            char *newPreview = (char *)realloc(preview, currentLen + lineBufferIndex + 1);
            if (newPreview != NULL) {
                preview = newPreview;
            }
        }

        if (currentLen > 0) {
            strcat(preview, "\n");
        }
        strcat(preview, lineBuffer);
    }

    jstring result = (*env)->NewStringUTF(env, preview);
    free(preview);
    (*env)->ReleaseStringUTFChars(env, content, contentStr);

    return result;
}

// 检查文件扩展名是否为lrc
JNIEXPORT jboolean JNICALL
Java_com_neko_music_util_LrcParser_nativeIsLrcFile(JNIEnv *env, jclass type, jstring fileName) {
    if (fileName == NULL) {
        return JNI_FALSE;
    }

    const char *fileNameStr = (*env)->GetStringUTFChars(env, fileName, 0);
    if (fileNameStr == NULL) {
        return JNI_FALSE;
    }

    int len = strlen(fileNameStr);
    jboolean result = JNI_FALSE;

    if (len >= 4) {
        const char *ext = fileNameStr + len - 4;
        if (strcmp(ext, ".lrc") == 0 || strcmp(ext, ".LRC") == 0) {
            result = JNI_TRUE;
        }
    }

    (*env)->ReleaseStringUTFChars(env, fileName, fileNameStr);
    return result;
}
