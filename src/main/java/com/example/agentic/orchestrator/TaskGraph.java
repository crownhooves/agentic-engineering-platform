package com.example.agentic.orchestrator;

import java.util.*;

/** Immutable, validated DAG: no duplicate keys, no unknown/self dependencies, no cycles. */
public final class TaskGraph {

    private final Map<String, TaskSpec> tasks;
    private final Map<String, Set<String>> dependents;
    private final List<TaskSpec> order;

    public TaskGraph(Collection<TaskSpec> specs) {
        Map<String, TaskSpec> byKey = new LinkedHashMap<>();
        for (TaskSpec s : specs) {
            if (byKey.putIfAbsent(s.key(), s) != null) {
                throw new InvalidGraphException("Duplicate task key: " + s.key());
            }
        }
        Map<String, Set<String>> rev = new LinkedHashMap<>();
        byKey.keySet().forEach(k -> rev.put(k, new LinkedHashSet<>()));
        for (TaskSpec s : byKey.values()) {
            for (String dep : s.dependsOn()) {
                if (dep.equals(s.key())) {
                    throw new InvalidGraphException("Task " + s.key() + " depends on itself");
                }
                if (!byKey.containsKey(dep)) {
                    throw new InvalidGraphException("Task " + s.key() + " depends on unknown task " + dep);
                }
                rev.get(dep).add(s.key());
            }
        }
        this.tasks = Collections.unmodifiableMap(byKey);
        this.dependents = rev;
        this.order = Collections.unmodifiableList(topoSort());
    }

    /** Kahn's algorithm; stable with respect to insertion order. */
    private List<TaskSpec> topoSort() {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        tasks.values().forEach(t -> inDegree.put(t.key(), t.dependsOn().size()));
        Deque<String> queue = new ArrayDeque<>();
        inDegree.forEach((k, d) -> { if (d == 0) queue.add(k); });

        List<TaskSpec> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String k = queue.poll();
            sorted.add(tasks.get(k));
            for (String next : dependents.get(k)) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) queue.add(next);
            }
        }
        if (sorted.size() != tasks.size()) {
            Set<String> stuck = new TreeSet<>(tasks.keySet());
            sorted.forEach(t -> stuck.remove(t.key()));
            throw new InvalidGraphException("Dependency cycle detected involving tasks " + stuck);
        }
        return sorted;
    }

    public TaskSpec get(String key) {
        TaskSpec t = tasks.get(key);
        if (t == null) throw new NoSuchElementException("Unknown task " + key);
        return t;
    }

    public Collection<TaskSpec> tasks() { return tasks.values(); }
    public int size() { return tasks.size(); }
    public List<TaskSpec> topologicalOrder() { return order; }
    public Set<String> dependenciesOf(String key) { return get(key).dependsOn(); }
    public Set<String> dependentsOf(String key) {
        get(key);
        return Collections.unmodifiableSet(dependents.get(key));
    }

    /** All tasks that (directly or indirectly) need the given task's output. */
    public Set<String> transitiveDependents(String key) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>(dependentsOf(key));
        while (!stack.isEmpty()) {
            String k = stack.pop();
            if (seen.add(k)) stack.addAll(dependents.get(k));
        }
        return seen;
    }

    /** Depth of each task (roots = 0); tasks on the same level can run in parallel. Used by the UI. */
    public Map<String, Integer> levels() {
        Map<String, Integer> level = new LinkedHashMap<>();
        for (TaskSpec t : order) {
            int l = t.dependsOn().stream().mapToInt(level::get).max().orElse(-1) + 1;
            level.put(t.key(), l);
        }
        return level;
    }
}