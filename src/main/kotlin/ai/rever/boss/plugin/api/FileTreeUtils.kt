package ai.rever.boss.plugin.api

/**
 * Utility functions for immutable file tree operations.
 *
 * These utilities work with FileNodeData for proper Compose state management.
 */
object FileTreeUtils {

    /**
     * Finds a node by its path using recursive DFS.
     *
     * @param root The root node to search from
     * @param targetPath The path to find
     * @return The found node, or null if not found
     */
    fun findNodeByPath(root: FileNodeData?, targetPath: String): FileNodeData? {
        if (root == null) return null
        if (root.path == targetPath) return root
        for (child in root.children) {
            val found = findNodeByPath(child, targetPath)
            if (found != null) return found
        }
        return null
    }

    /**
     * Creates a new tree with the node at targetPath updated using the provided transform.
     * This ensures immutable state updates for proper Compose recomposition.
     * Only nodes along the path are copied; other subtrees are shared.
     *
     * @param root The root node of the tree
     * @param targetPath The path of the node to update
     * @param update The transform function to apply to the target node
     * @return A new tree with the updated node
     */
    fun updateNodeAtPath(
        root: FileNodeData,
        targetPath: String,
        update: (FileNodeData) -> FileNodeData
    ): FileNodeData {
        if (root.path == targetPath) {
            return update(root)
        }

        // Recursively update, creating new nodes along the path to the target
        return root.copy(
            children = root.children.map { child ->
                if (targetPath.startsWith(child.path + "/") || targetPath == child.path) {
                    updateNodeAtPath(child, targetPath, update)
                } else {
                    child
                }
            }
        )
    }
}
