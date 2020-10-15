package ru.yogago.petbase.model

import android.util.Log
import androidx.lifecycle.ViewModel
import de.blox.graphview.Edge
import de.blox.graphview.Graph
import de.blox.graphview.Node
import kotlinx.coroutines.*
import ru.yogago.petbase.data.ApiPet
import ru.yogago.petbase.data.MyNode
import ru.yogago.petbase.service.ApiFactory
import ru.yogago.petbase.ui.graph.GraphViewModel
import kotlin.coroutines.CoroutineContext

class GraphModel: CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job
    private val LOG_TAG: String = "myLog"
    private lateinit var graphViewModel: GraphViewModel
    private val service = ApiFactory.API
    private val myGraph = Graph()
    private val nodes: MutableMap<Long, MyNode> = mutableMapOf()
    private val edges: MutableMap<String, Edge> = mutableMapOf()

    private suspend fun getAllNodes(id: Long) {
        val apiPet = loadPetForGraph(id)
        val children: List<Long> = apiPet?.children!!
        val parents = apiPet.parents
        addChildrenNodes(children)
        addParentsNodes(parents)
        nodes.forEach {
            Log.d(LOG_TAG,"GraphModel - getAllNodes node: " + (it.value.node.data as ApiPet).name)
        }
    }

    private suspend fun addChildrenNodes(children: List<Long>) {
        if (children.isNotEmpty()) {
            children.forEach {
                val apiPetChild = loadPetForGraph(it)
                Log.d(LOG_TAG, "addChildrenNodes: " + apiPetChild?.name)
                if (nodes[it] == null) {
                    nodes[it] = MyNode(
                        node = Node(apiPetChild)
                    )
                    addChildrenNodes(apiPetChild?.children!!)
                    addParentsNodes(apiPetChild.parents)
                }
            }
        }
    }

    private suspend fun addParentsNodes(parents: List<Long>) {
        if (parents.isNotEmpty()) {
            parents.forEach {
                val apiPetParent = loadPetForGraph(it)
                Log.d(LOG_TAG, "addChildrenNodes: " + apiPetParent?.name)
                if (nodes[it] == null) {
                    nodes[it] = MyNode(
                        node = Node(apiPetParent)
                    )
                    addParentsNodes(apiPetParent?.parents!!)
                    addChildrenNodes(apiPetParent.children)
                }
            }
        }
    }

    private fun graphCreator(id: Long) {
        launch {
            getAllNodes(id)
            edgesChildren()
            edgesParents()

            graphViewModel.graph.postValue(myGraph)
            graphViewModel.graphFlag.postValue(true)
        }
    }

    private fun edgesChildren() {
        nodes.forEach {
            it.value.isVisitedChildren = true
            val children = (it.value.node.data as ApiPet).children
            if (children.isNotEmpty()) {
                children.forEach { id ->
                    val edge = Edge(it.value.node, nodes[id]?.node!!)
                    val keyEdges = "" + it.value.node.hashCode() + nodes[id]?.node.hashCode()
                    if (edges[keyEdges] == null){
                        edges[keyEdges] = edge
                        myGraph.addEdge(edge)
                    }
                }
            }
        }
    }

    private fun edgesParents() {
        nodes.forEach {
            it.value.isVisitedParent = true
            val parents = (it.value.node.data as ApiPet).parents
            if (parents.isNotEmpty()) {
                parents.forEach {id ->
                    val edge = Edge(nodes[id]?.node!!, it.value.node)
                    val keyEdges = "" + nodes[id]?.node.hashCode() + it.value.node.hashCode()
                    if (edges[keyEdges] == null){
                        edges[keyEdges] = edge
                        myGraph.addEdge(edge)
                    }

                }
            }
        }
    }

    fun loadGraph(baseId: Long) {
        launch {
            val apiPet = loadPetForGraph(baseId)
            graphCreator(apiPet?.id!!)
            graphViewModel.genealogyHead.postValue(apiPet.name)
        }
    }

    private suspend fun loadPetForGraph(baseId: Long): ApiPet? {
        val petRequest = service.getPetAsync(baseId.toString())
        return try {
            val response = petRequest.await()
            if(response.isSuccessful) {
                val apiPet = response.body()!!
                Log.d(LOG_TAG, "GraphModel - loadPetForGraph: $apiPet")
                apiPet
            } else {
                Log.d(LOG_TAG,"GraphModel - loadPetForGraph error: " + response.errorBody())
                null
            }
        } catch (e: Exception) {
            Log.d(LOG_TAG, "GraphModel - loadPetForGraph Exception: $e")
            null
        }
    }

    fun setViewModel(m: ViewModel) {
        this.graphViewModel = m as GraphViewModel
        Log.d(LOG_TAG, "GraphViewModel: " + this.graphViewModel)
    }

}
