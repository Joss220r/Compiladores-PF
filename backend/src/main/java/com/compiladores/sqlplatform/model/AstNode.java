package com.compiladores.sqlplatform.model;

import java.util.List;
import java.util.Map;

public class AstNode {

    private String type;
    private String value;
    private Map<String, Object> attributes;
    private List<AstNode> children;

    public AstNode(String type, String value, Map<String, Object> attributes, List<AstNode> children) {
        this.type = type;
        this.value = value;
        this.attributes = attributes;
        this.children = children;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public List<AstNode> getChildren() {
        return children;
    }
}
