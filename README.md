# sibgen

Parse markdown into a Scala 3 ADT and render it as a self-contained HTML page in an iA Writer–inspired style.

```
sbt 'runMain render file.md'    # → file.html
sbt 'runMain inspect file.md'   # ADT pretty-print
```

Built on [commonmark-java](https://github.com/commonmark/commonmark-java).
