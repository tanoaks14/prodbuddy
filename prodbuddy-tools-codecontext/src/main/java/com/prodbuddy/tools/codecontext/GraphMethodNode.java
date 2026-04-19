package com.prodbuddy.tools.codecontext;

public record GraphMethodNode(String id, String classFqn, String name, String signature, String filePath, String annotations, int startLine, int endLine) {

}
