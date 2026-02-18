/* config.h - MeCab configuration for Android */
#ifndef CONFIG_H_
#define CONFIG_H_

#define HAVE_STRING_H 1
#define HAVE_STDLIB_H 1
#define HAVE_UNISTD_H 1
#define HAVE_FCNTL_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_MMAN_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_DIRENT_H 1
#define HAVE_CTYPE_H 1

#define HAVE_MMAP 1
#define HAVE_GETENV 1
#define HAVE_OPENDIR 1

#define SIZEOF_CHAR 1
#define SIZEOF_SHORT 2
#define SIZEOF_INT 4
#define SIZEOF_LONG 8
#define SIZEOF_LONG_LONG 8
#define SIZEOF_SIZE_T 8

#define PACKAGE "mecab"
#define VERSION "0.996"
#define DIC_VERSION 102

/* Android doesn't have iconv in the standard library */
#define HAVE_ICONV 0

#endif /* CONFIG_H_ */
