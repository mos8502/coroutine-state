package hu.nemi.store

/**
 * Node reference that allows modification an query of the parentNode and it's value
 */
internal interface Node<T : Any> {
    val node: Lens<StateNode<Any>, StateNode<T>>
    val value: Lens<StateNode<Any>, T>

    fun <C : Any> withChild(key: Any, init: () -> C): Node<C>

    companion object {
        /**
         * Factory method for constructing root parentNode
         */
        operator fun <T : Any> invoke(): Node<T> = object : Node<T> {
            override val node: Lens<StateNode<Any>, StateNode<T>> = Lens(
                    get = { it as StateNode<T> },
                    set = { child -> { parent -> parent.copy(value = child.value, children = child.children) } }
            )
            override val value: Lens<StateNode<Any>, T> = Lens(
                    get = { it.value as T },
                    set = { value -> { node -> node.copy(value = value) } }
            )

            override fun <C : Any> withChild(key: Any, init: () -> C): Node<C> = DefaultNode(parent = this, key = key, init = init)
        }

        operator fun <T : Any, V : Any> invoke(outerNode: Node<T>, lens: Lens<T, V>): Node<V> = object : Node<V> {
            override fun <C : Any> withChild(key: Any, init: () -> C): Node<C> = outerNode.withChild(key, init)

            override val value: Lens<StateNode<Any>, V> = outerNode.value + lens

            override val node: Lens<StateNode<Any>, StateNode<V>> = Lens(
                    get = {
                        with(outerNode.node(it)) {
                            StateNode(value = value, children = children, lens = lens)
                        }
                    },
                    set = { child ->
                        { parent ->
                            var p = outerNode.node(parent)
                            p = p.copy(children = child.children, value = lens(p.value, child.value))
                            outerNode.node(parent, p)
                        }
                    }
            )

        }
    }
}

internal interface StateNode<V : Any> {
    val value: V
    val children: Map<Any, StateNode<*>>

    fun copy(value: V = this.value, children: Map<Any, StateNode<*>> = this.children): StateNode<V>

    companion object {
        operator fun <V : Any> invoke(value: V, children: Map<Any, StateNode<*>>): StateNode<V> =
                DefaultStateNode(value = value, children = children)

        operator fun <V : Any, P : Any> invoke(value: P, children: Map<Any, StateNode<*>>, lens: Lens<P, V>): StateNode<V> =
                LensStateNode(parentValue = value, children = children, lens = lens)

        operator fun invoke(value: Any): StateNode<Any> = StateNode(value = value, children = emptyMap())
    }
}

private data class DefaultStateNode<V : Any>(override val value: V, override val children: Map<Any, StateNode<*>>) : StateNode<V>

private data class LensStateNode<V : Any, P : Any>(val parentValue: P,
                                                   override val children: Map<Any, StateNode<*>>,
                                                   val lens: Lens<P, V>) : StateNode<V> {
    override val value: V = lens(parentValue)

    override fun copy(value: V, children: Map<Any, StateNode<*>>): StateNode<V> {
        return copy(parentValue = lens(parentValue, value), children = children)
    }
}

/**
 * Default implementation of a [Node]
 */
private data class DefaultNode<P : Any, T : Any>(val parent: Node<P>, val key: Any, val init: () -> T) : Node<T> {
    override val node: Lens<StateNode<Any>, StateNode<T>> = parent.node + Lens(
            get = {
                it.children[key] as? StateNode<T>
                        ?: StateNode(value = init(), children = emptyMap())
            },
            set = { child -> { parent -> parent.copy(children = parent.children + (key to child)) } }
    )

    override val value: Lens<StateNode<Any>, T> = node + Lens(
            get = { it.value },
            set = { value -> { node -> node.copy(value = value) } }
    )

    override fun <C : Any> withChild(key: Any, init: () -> C): Node<C> = DefaultNode(parent = this, key = key, init = init)
}
