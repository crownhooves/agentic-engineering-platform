package com.example.agentic.agents.dto;

import java.util.List;

final class Lists {
    private Lists() {}
    static <T> List<T> nn(List<T> in) { return in == null ? List.of() : List.copyOf(in); }
}