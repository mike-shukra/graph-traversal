package ru.yogago.petbase.model

import android.util.Log
import androidx.lifecycle.ViewModel
import de.blox.graphview.Edge
import de.blox.graphview.Graph
import de.blox.graphview.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.yogago.petbase.data.ApiPet
import ru.yogago.petbase.data.MyNode
import ru.yogago.petbase.service.ApiFactory
import ru.yogago.petbase.ui.graph.GraphViewModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GraphModel {

    private val LOG_TAG: String = "myLog"
    private lateinit var graphViewModel: GraphViewModel
    private val service = ApiFactory.API
    private val myGraph = Graph()
    private val nodes: MutableMap<Long, MyNode> = mutableMapOf()
    private val edges: MutableMap<String, Edge> = mutableMapOf()

    private suspend fun getAllNodes(id: Long) {
        val apiPet = loadPetForGraph(id)
        val children: List<Long> = apiPet.children!!
        val parents = apiPet.parents!!
        addChildrenNodes(children)
        addParentsNodes(parents)
        nodes.forEach {
            Log.d(LOG_TAG,"GraphModel - getAllNodes node: " + (it.value.node?.data as ApiPet).name)
        }
    }

    private suspend fun addChildrenNodes(children: List<Long>) {
        if (children.isNotEmpty()) {
            children.forEach {
                val apiPetChild = loadPetForGraph(it)
                Log.d(LOG_TAG, "addChildrenNodes: " + apiPetChild.name)
                if (nodes[it] == null) {
                    nodes[it] = MyNode(
                        node = Node(apiPetChild)
                    )
                    addChildrenNodes(apiPetChild.children!!)
                    addParentsNodes(apiPetChild.parents!!)
                }
            }
        }
    }

    private suspend fun addParentsNodes(parents: List<Long>) {
        if (parents.isNotEmpty()) {
            parents.forEach {
                val apiPetParent = loadPetForGraph(it)
                Log.d(LOG_TAG, "addChildrenNodes: " + apiPetParent.name)
                if (nodes[it] == null) {
                    nodes[it] = MyNode(
                        node = Node(apiPetParent)
                    )
                    addParentsNodes(apiPetParent.parents!!)
                    addChildrenNodes(apiPetParent.children!!)
                }
            }
        }
    }

    private fun graphCreator(id: Long) {
        GlobalScope.launch(Dispatchers.Main) {
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
            val children = (it.value.node?.data as ApiPet).children
            if (children != null) {
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
    }

    private fun edgesParents() {
        nodes.forEach {
            it.value.isVisitedParent = true
            val parents = (it.value.node?.data as ApiPet).parents
            if (parents != null) {
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
    }

    fun loadGraph(baseId: Long) {
        GlobalScope.launch(Dispatchers.IO) {
            val apiPet = loadPetForGraph(baseId)
            graphCreator(apiPet.id!!)
            graphViewModel.genealogyHead.postValue("Генеалогия питомца " + apiPet.name + ":")
        }
    }

    private suspend fun loadPetForGraph(baseId: Long): ApiPet {
        return suspendCoroutine { continuation ->
            GlobalScope.launch(Dispatchers.Main) {
                val petRequest = service.getPetAsync(baseId.toString())
                try {
                    val response = petRequest.await()
                    if(response.isSuccessful) {
                        val apiPet = response.body()!!
                        Log.d(LOG_TAG, "GraphModel - loadPetForGraph: $apiPet")
                        continuation.resume(apiPet)
                    } else {
                        Log.d(LOG_TAG,"GraphModel - loadPetForGraph error: " + response.errorBody())
                        val apiPetError = ApiPet(error = response.errorBody().toString())
                        continuation.resume(apiPetError)
                    }
                }
                catch (e: Exception) {
                    Log.d(LOG_TAG, "GraphModel - loadPetForGraph Exception: $e")
                    val apiPetError = ApiPet(error = e.toString())
                    continuation.resume(apiPetError)
                }
            }
        }
    }

    fun setViewModel(m: ViewModel) {
        this.graphViewModel = m as GraphViewModel
        Log.d(LOG_TAG, "GraphViewModel: " + this.graphViewModel)
    }

}

