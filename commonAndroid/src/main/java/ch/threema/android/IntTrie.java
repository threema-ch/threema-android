package ch.threema.android;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

/**
 * A trie data structure with integers as path elements that can store data in the leaf nodes.
 * <p>
 * Note that the path element type (int in this case) could easily be made generic, but that would
 * have the drawback of having to use the non-primitive path type `Integer[]` instead of `int[]`.
 * This creates the situation that path elements could be null, requiring runtime-checks.
 * Additionally, storage of node children would have to be done with a HashMap instead of a
 * SparseArray, leading to higher memory consumption.
 */
public class IntTrie<T> {

    private class Node {
        @NonNull
        final SparseArrayCompat<Node> children;
        @Nullable
        T value;

        Node(@Nullable T value) {
            this.children = new SparseArrayCompat<>();
            this.value = value;
        }

        @Override
        public String toString() {
            return "Node{" +
                "value=" + value +
                ", children=" + children +
                '}';
        }
    }

    /**
     * Wrapper around a value.
     * <p>
     * It contains a value of type V, plus the information whether
     * the value node is a leaf or not.
     * <p>
     * The value may be null if a node exists for the chosen path,
     * but it does not contain a value.
     */
    public static class Value<V> {
        @Nullable
        private V value;
        private boolean isLeaf;

        public Value(@Nullable V value, boolean isLeaf) {
            this.value = value;
            this.isLeaf = isLeaf;
        }

        @Nullable
        public V getValue() {
            return value;
        }

        public boolean isLeaf() {
            return isLeaf;
        }
    }

    @NonNull
    private Node root;

    public IntTrie() {
        this.root = new Node(null);
    }

    /**
     * Insert a new value into the trie.
     *
     * @param path  The path to that value.
     * @param value The value to be stored at the end of the path.
     */
    public void insert(@NonNull int[] path, @NonNull T value) {
        // Do not insert empty arrays
        if (path.length == 0) {
            return;
        }

        Node currentNode = this.root;
        for (int p : path) {
            Node foundNode = currentNode.children.get(p);
            if (foundNode != null) {
                currentNode = foundNode;
            } else {
                final Node newNode = new Node(null);
                currentNode.children.put(p, newNode);
                currentNode = newNode;
            }
        }
        currentNode.value = value;
    }

    /**
     * Return the value at the specified path, or null.
     */
    @Nullable
    public Value<T> get(@NonNull int[] path) {
        // No need for checking empty arrays
        if (path.length == 0) {
            return null;
        }

        Node currentNode = this.root;
        for (int p : path) {
            final Node foundNode = currentNode.children.get(p);
            if (foundNode == null) {
                return null;
            }
            currentNode = foundNode;
        }
        return new Value<>(currentNode.value, currentNode.children.isEmpty());
    }

    /**
     * Return the value at the specified path, or null.
     * The path may not contain any null values!
     */
    @Nullable
    public Value<T> get(@NonNull Iterable<Integer> path) {
        Node currentNode = this.root;
        for (int p : path) {
            final Node foundNode = currentNode.children.get(p);
            if (foundNode == null) {
                return null;
            }
            currentNode = foundNode;
        }
        return new Value<>(currentNode.value, currentNode.children.isEmpty());
    }

    /**
     * Return the trie contains an element at the specified path.
     */
    public boolean contains(@NonNull int[] path) {
        final Value value = this.get(path);
        return value != null && value.getValue() != null;
    }
}
