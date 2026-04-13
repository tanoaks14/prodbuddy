package com.prodbuddy.tools.codecontext;

import java.util.Collections;
import java.util.List;

public record JavaGraphSnapshot(
        List<GraphClassNode> classes,
        List<GraphMethodNode> methods,
        List<GraphDefineEdge> defines,
        List<GraphInheritanceEdge> inherits,
        List<GraphCallEdge> calls
        ) {

    public JavaGraphSnapshot     {
        classes = classes == null ? List.of() : Collections.unmodifiableList(classes);
        methods = methods == null ? List.of() : Collections.unmodifiableList(methods);
        defines = defines == null ? List.of() : Collections.unmodifiableList(defines);
        inherits = inherits == null ? List.of() : Collections.unmodifiableList(inherits);
        calls = calls == null ? List.of() : Collections.unmodifiableList(calls);
    }
}
