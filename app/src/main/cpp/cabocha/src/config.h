/* config.h - CaboCha configuration for Android */
#ifndef CABOCHA_CONFIG_H_
#define CABOCHA_CONFIG_H_

#define HAVE_STRING_H 1
#define HAVE_STDLIB_H 1
#define HAVE_UNISTD_H 1
#define HAVE_FCNTL_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_MMAN_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_DIRENT_H 1
#define HAVE_CTYPE_H 1
#define HAVE_STDINT_H 1
#define HAVE_MMAP 1
#define HAVE_GETENV 1

#define PACKAGE "cabocha"
#define VERSION "0.69"
#define MODEL_VERSION 102

/* Android には iconv がないため定義しない */
/* #undef HAVE_ICONV */
/* #undef HAVE_ICONV_EUC_JP_MS */
/* #undef HAVE_ICONV_CP932 */

#endif /* CABOCHA_CONFIG_H_ */
