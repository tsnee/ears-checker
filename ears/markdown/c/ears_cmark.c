/* The checker's one entry point into cmark-gfm, the same on every platform (compiled into the
   native binary, and into the WebAssembly module the JVM and Node run). ears_tree parses Markdown
   with the table extension and writes out the parts of the tree the Markdown reader uses, so the
   reader, shared Scala code, needs no other call into the parser.

   The output is two records per node, one when the walk enters it and one when it leaves it, in
   document order, so the records nest as the tree does; each record is a line of its own:

     E <type> <start line> <start column> <end line> <end column> <length>:<literal>
     X

   where <type> is cmark's name for the node's type (paragraph, text, table_row, ...), the
   positions are cmark's (1-based lines, byte columns, 0 where cmark has none), and <literal> is
   the node's literal text (empty for most nodes), its length in bytes first, so it's never
   escaped. The output ends with a NUL byte, which cmark never puts in text (it replaces one in the
   input with U+FFFD). The walk is a loop, so no depth of nesting grows the stack. */
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#include "cmark-gfm-core-extensions.h"
#include "cmark-gfm.h"

void *ears_alloc(size_t size) { return malloc(size); }

void ears_free(void *pointer) { free(pointer); }

/* cmark-gfm's release, such as "0.29.0.gfm.13". */
const char *ears_version(void) { return cmark_version_string(); }

typedef struct {
  char *data;
  size_t length;
  size_t capacity;
  int failed;
} buffer;

static void put(buffer *out, const char *bytes, size_t count) {
  if (out->failed) {
    return;
  }
  if (count + 1 > out->capacity - out->length) {
    size_t capacity = out->capacity > 0 ? out->capacity : 4096;
    while (count + 1 > capacity - out->length) {
      if (capacity > ((size_t)-1) / 2) {
        out->failed = 1;
        return;
      }
      capacity *= 2;
    }
    char *grown = realloc(out->data, capacity);
    if (grown == NULL) {
      out->failed = 1;
      return;
    }
    out->data = grown;
    out->capacity = capacity;
  }
  memcpy(out->data + out->length, bytes, count);
  out->length += count;
  out->data[out->length] = '\0';
}

static void put_string(buffer *out, const char *string) { put(out, string, strlen(string)); }

/* A space and the number in decimal, as snprintf's " %ld" writes it, but by hand: on the JVM,
   snprintf's printf_core compiles to a method too big for HotSpot to compile, so it would run
   interpreted, five times for every node. */
static void put_number(buffer *out, long number) {
  char digits[24];
  size_t at = sizeof digits;
  unsigned long value = number < 0 ? 0UL - (unsigned long)number : (unsigned long)number;
  do {
    digits[--at] = (char)('0' + value % 10);
    value /= 10;
  } while (value > 0);
  if (number < 0) {
    digits[--at] = '-';
  }
  digits[--at] = ' ';
  put(out, digits + at, sizeof digits - at);
}

static void put_enter(buffer *out, cmark_node *node) {
  const char *literal = cmark_node_get_literal(node);
  size_t literal_length = literal != NULL ? strlen(literal) : 0;
  put_string(out, "E ");
  put_string(out, cmark_node_get_type_string(node));
  put_number(out, cmark_node_get_start_line(node));
  put_number(out, cmark_node_get_start_column(node));
  put_number(out, cmark_node_get_end_line(node));
  put_number(out, cmark_node_get_end_column(node));
  put_number(out, (long)literal_length);
  put_string(out, ":");
  if (literal_length > 0) {
    put(out, literal, literal_length);
  }
  put_string(out, "\n");
}

/* cmark-gfm registers its extensions in global lists, unsynchronized, so two threads registering
   at once corrupt them (cmark aborts with "flag initialization error"); registering once, before
   any parse, makes every later call safe on any thread. */
static pthread_once_t registration = PTHREAD_ONCE_INIT;

static void register_extensions(void) { cmark_gfm_core_extensions_ensure_registered(); }

/* The records of `length` bytes of UTF-8 Markdown at `text`, NUL-terminated, for the caller to
   free with ears_free; or NULL if memory ran out. Safe to call from any thread. */
char *ears_tree(const char *text, size_t length) {
  if (pthread_once(&registration, register_extensions) != 0) {
    return NULL;
  }
  /* CMARK_OPT_SOURCEPOS: without it, cmark-gfm doesn't move an inline node's end line past the
     line endings inside it (a code span or inline HTML across lines), so the reader would place
     everything before such a node a line late. */
  cmark_parser *parser = cmark_parser_new(CMARK_OPT_DEFAULT | CMARK_OPT_SOURCEPOS);
  if (parser == NULL) {
    return NULL;
  }
  cmark_syntax_extension *table = cmark_find_syntax_extension("table");
  if (table == NULL || !cmark_parser_attach_syntax_extension(parser, table)) {
    cmark_parser_free(parser);
    return NULL;
  }
  cmark_parser_feed(parser, text, length);
  cmark_node *document = cmark_parser_finish(parser);
  buffer out = {NULL, 0, 0, 0};
  put(&out, "", 0);
  if (document != NULL) {
    cmark_node *node = document;
    for (;;) {
      put_enter(&out, node);
      cmark_node *child = cmark_node_first_child(node);
      if (child != NULL) {
        node = child;
        continue;
      }
      put_string(&out, "X\n");
      while (node != document && cmark_node_next(node) == NULL) {
        node = cmark_node_parent(node);
        put_string(&out, "X\n");
      }
      if (node == document) {
        break;
      }
      node = cmark_node_next(node);
    }
    cmark_node_free(document);
  }
  cmark_parser_free(parser);
  if (out.failed) {
    free(out.data);
    return NULL;
  }
  return out.data;
}
