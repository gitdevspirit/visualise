/*
 * Visualise launch agent (JVMTI).
 *
 * Loaded by the JVM itself via  -agentpath:<lib>=dir=<install dir>  so it is in the process before the
 * game's classes exist. It does three things and nothing else:
 *
 *   1. puts visualise-agent.jar on the bootstrap search path (visible to every classloader),
 *   2. forwards every class-file load to  visualise.boot.Boot.transform  (the Java engine does the work),
 *   3. exposes JNI DefineClass to Java as  Boot.define0  so the payload can be defined in the game's loader.
 *
 * Option  probe  (e.g. -agentpath:lib=probe) only prints a marker and exits; used by tools/doctor.sh.
 */
#include <jni.h>
#include <jvmti.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define EXPORT __attribute__((visibility("default")))
#define BOOT_CLASS "visualise/boot/Boot"

static JavaVM   *g_vm;
static jvmtiEnv *g_jvmti;
static char     *g_options;
static volatile int g_ready;          /* set once Boot.init has run */
static jclass    g_boot;
static jmethodID g_transform;
static __thread int t_busy;           /* re-entrancy guard: classes loaded while we transform are skipped */

static int skip_name(const char *n) {
    return strncmp(n, "java/", 5) == 0 || strncmp(n, "javax/", 6) == 0 || strncmp(n, "jdk/", 4) == 0 ||
           strncmp(n, "sun/", 4) == 0 || strncmp(n, "com/sun/", 8) == 0 || strncmp(n, "visualise/", 10) == 0;
}

/* Boot.define0(ClassLoader, String binaryName, byte[]) -> Class */
static jclass JNICALL native_define(JNIEnv *env, jclass self, jobject loader, jstring name, jbyteArray data) {
    const char *cname = (*env)->GetStringUTFChars(env, name, NULL);
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    jclass result = (*env)->DefineClass(env, cname, loader, buf, len);   /* leaves an exception pending on failure */
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, name, cname);
    return result;
}

static void JNICALL on_vm_init(jvmtiEnv *jvmti, JNIEnv *env, jthread thread) {
    (void)jvmti; (void)thread;
    jclass boot = (*env)->FindClass(env, BOOT_CLASS);
    if (!boot) { (*env)->ExceptionDescribe(env); (*env)->ExceptionClear(env); fprintf(stderr, "[visualise] Boot class not found\n"); return; }
    g_boot = (jclass)(*env)->NewGlobalRef(env, boot);

    JNINativeMethod nm = { "define0", "(Ljava/lang/ClassLoader;Ljava/lang/String;[B)Ljava/lang/Class;", (void *)native_define };
    if ((*env)->RegisterNatives(env, g_boot, &nm, 1) != 0) { (*env)->ExceptionClear(env); fprintf(stderr, "[visualise] RegisterNatives failed\n"); return; }

    g_transform = (*env)->GetStaticMethodID(env, g_boot, "transform", "(Ljava/lang/ClassLoader;Ljava/lang/String;[B)[B");
    jmethodID init = (*env)->GetStaticMethodID(env, g_boot, "init", "(Ljava/lang/String;)V");
    if (!g_transform || !init) { (*env)->ExceptionClear(env); fprintf(stderr, "[visualise] Boot methods missing\n"); return; }

    t_busy = 1;
    jstring opts = (*env)->NewStringUTF(env, g_options ? g_options : "");
    (*env)->CallStaticVoidMethod(env, g_boot, init, opts);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionDescribe(env); (*env)->ExceptionClear(env); fprintf(stderr, "[visualise] init failed\n"); t_busy = 0; return; }
    t_busy = 0;
    g_ready = 1;
}

static void JNICALL on_class_load(jvmtiEnv *jvmti, JNIEnv *env, jclass class_being_redefined, jobject loader,
                                  const char *name, jobject protection_domain, jint class_data_len,
                                  const unsigned char *class_data, jint *new_class_data_len,
                                  unsigned char **new_class_data) {
    (void)class_being_redefined; (void)protection_domain;
    if (!g_ready || t_busy || !name || !env || skip_name(name)) return;
    t_busy = 1;

    jbyteArray in = (*env)->NewByteArray(env, class_data_len);
    jstring jname = (*env)->NewStringUTF(env, name);
    if (in && jname) {
        (*env)->SetByteArrayRegion(env, in, 0, class_data_len, (const jbyte *)class_data);
        jbyteArray out = (jbyteArray)(*env)->CallStaticObjectMethod(env, g_boot, g_transform, loader, jname, in);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        } else if (out) {
            jsize n = (*env)->GetArrayLength(env, out);
            unsigned char *mem = NULL;
            if ((*jvmti)->Allocate(jvmti, n, &mem) == JVMTI_ERROR_NONE) {
                (*env)->GetByteArrayRegion(env, out, 0, n, (jbyte *)mem);
                *new_class_data = mem;
                *new_class_data_len = n;
            }
            (*env)->DeleteLocalRef(env, out);
        }
    }
    if (in) (*env)->DeleteLocalRef(env, in);
    if (jname) (*env)->DeleteLocalRef(env, jname);
    t_busy = 0;
}

/* Value of key in "a=b,c=d" style options (copied into out). */
static int option_value(const char *opts, const char *key, char *out, size_t cap) {
    size_t kl = strlen(key);
    const char *p = opts;
    while (p && *p) {
        if (strncmp(p, key, kl) == 0 && p[kl] == '=') {
            p += kl + 1;
            size_t n = strcspn(p, ",");
            if (n >= cap) n = cap - 1;
            memcpy(out, p, n); out[n] = 0;
            return 1;
        }
        p = strchr(p, ',');
        if (p) p++;
    }
    return 0;
}

EXPORT jint Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
    (void)reserved;
    g_vm = vm;
    if (options && strstr(options, "probe")) {
        fprintf(stderr, "VISUALISE_PROBE_AGENT_LOADED options=[%s]\n", options);
        fflush(stderr);
        return JNI_OK;
    }

    char dir[4096];
    if (!options || !option_value(options, "dir", dir, sizeof dir)) {
        fprintf(stderr, "[visualise] missing dir=<install dir> option\n");
        return JNI_ERR;
    }
    g_options = strdup(options);

    if ((*vm)->GetEnv(vm, (void **)&g_jvmti, JVMTI_VERSION_1_2) != JNI_OK) { fprintf(stderr, "[visualise] no JVMTI\n"); return JNI_ERR; }

    jvmtiCapabilities caps;
    memset(&caps, 0, sizeof caps);
    caps.can_generate_all_class_hook_events = 1;
    if ((*g_jvmti)->AddCapabilities(g_jvmti, &caps) != JVMTI_ERROR_NONE) { fprintf(stderr, "[visualise] capabilities refused\n"); return JNI_ERR; }

    char jar[4200];
    snprintf(jar, sizeof jar, "%s/visualise-agent.jar", dir);
    if ((*g_jvmti)->AddToBootstrapClassLoaderSearch(g_jvmti, jar) != JVMTI_ERROR_NONE) { fprintf(stderr, "[visualise] cannot add %s\n", jar); return JNI_ERR; }

    jvmtiEventCallbacks cb;
    memset(&cb, 0, sizeof cb);
    cb.VMInit = on_vm_init;
    cb.ClassFileLoadHook = on_class_load;
    (*g_jvmti)->SetEventCallbacks(g_jvmti, &cb, (jint)sizeof cb);
    (*g_jvmti)->SetEventNotificationMode(g_jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL);
    (*g_jvmti)->SetEventNotificationMode(g_jvmti, JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);
    return JNI_OK;
}
