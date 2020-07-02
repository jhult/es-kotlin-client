package io.inbot.eskotlinwrapper.manual

import io.inbot.eskotlinwrapper.AbstractElasticSearchTest
import io.inbot.eskotlinwrapper.AsyncSearchResults
import io.inbot.eskotlinwrapper.dsl.matchAll
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.search.dsl
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.asyncIndexRepository
import org.elasticsearch.client.indices.ReloadAnalyzersRequest
import org.elasticsearch.client.indices.ReloadAnalyzersResponse
import org.elasticsearch.client.reloadAnalyzersAsync
import org.elasticsearch.common.xcontent.XContentType
import org.junit.jupiter.api.Test

@Suppress("UNUSED_VARIABLE", "NAME_SHADOWING")
class CoRoutinesManualTest : AbstractElasticSearchTest(indexPrefix = "asyncthings") {
    private data class Thing(val title: String)

    @Test
    fun `coroutines manual`() {
        runBlocking {
            // we have to do this twice once for printing and once for using :-)
            val asyncRepo = esClient.asyncIndexRepository<Thing>("things", refreshAllowed = true)
            // make sure we get rid of the things index before running the rest of this
            asyncRepo.deleteIndex()
            asyncRepo.createIndex {
                source(
                    """
                            {
                              "settings": {
                                "index": {
                                  "number_of_shards": 3,
                                  "number_of_replicas": 0,
                                  "blocks": {
                                    "read_only_allow_delete": "false"
                                  }
                                }
                              },
                              "mappings": {
                                "properties": {
                                  "title": {
                                    "type": "text"
                                  }
                                }
                              }
                            }
                        """,
                    XContentType.JSON
                )
            }
            KotlinForExample.markdownPageWithNavigation(coroutinesPage) {
                +"""
                The RestHighLevelClient exposes asynchronous versions of most APIs that take a call back to process
                the response when it comes back. Using this is kind of boiler plate heavy. 
                
                Luckily, Kotlin has co-routines for asynchronous programming and this library provides co-routine 
                friendly versions of these functions. These `suspend` functions work pretty much the same way as their 
                synchronous version except they are marked as suspend and use a `SuspendingActionListener` that uses
                Kotlin's `suspendCancellableCoroutine` to wrap the callback that the rest high level client expects.
                
                As of Elasticsearch 7.5.0, all asynchronous calls return a `Cancellable` object that allows you to cancel
                the task. Using `suspendCancellableCoRoutine` uses this and this means that if you have some failure
                or abort a coroutine scope, all the running tasks are cancelled. 
                
                If you use an asynchronous server framework such as Ktor or Spring Boot 2.x (in reactive mode), you'll
                want to use the asynchronous functions.

                To support co-routines, this project is using a 
                [code generation plugin](https://github.com/jillesvangurp/es-kotlin-codegen-plugin) 
                to generate the co-routine friendly versions of each of the
                Rest High Level async functions. At this point most of them are covered. There are more than a hundred 
                of these. 
                
                As an example, here are three ways to use the reloadAnalyzers API:
            """

                block(false) {
                    // the synchronous version as provided by the RestHighLevel client
                    val ic = esClient.indices()
                    val response = ic.reloadAnalyzers(
                        ReloadAnalyzersRequest("myindex"), RequestOptions.DEFAULT
                    )

                    // the asynchronous version with a callback as provided by the
                    // RestHighLevel client
                    ic.reloadAnalyzersAsync(
                        ReloadAnalyzersRequest("myindex"),
                        RequestOptions.DEFAULT,
                        object : ActionListener<ReloadAnalyzersResponse> {
                            override fun onFailure(e: Exception) {
                                println("it failed")
                            }

                            override fun onResponse(response: ReloadAnalyzersResponse) {
                                println("it worked")
                            }
                        }
                    )

                    runBlocking {
                        // the coroutine friendly version using a function generated by the
                        // code generator plugin this is a suspend version so we put it in
                        // a runBlocking to get a coroutine scope use a more appropriate
                        // scope in your own application of course.
                        val response = ic.reloadAnalyzersAsync(
                            ReloadAnalyzersRequest("myindex"), RequestOptions.DEFAULT
                        )
                    }
                }
                +"""
                    ## AsyncIndexRepository
                    
                    In addition to having suspend versions of most functions in the `RestHighLevelClient`, the 
                    `IndexRepository` also has an `AsyncIndexRepository` counter part. The API of this is
                    similar to the regular repository. 
                """
                block {
                    // you can create a new repository via an extension function
                    val asyncRepo = esClient.asyncIndexRepository<Thing>("asyncthings")

                    // all functions on the asyncRepo are of course suspend so we
                    // need to run them in a co-routine
                    runBlocking {
                        asyncRepo.createIndex {
                            source(
                                """
                            {
                              "settings": {
                                "index": {
                                  "number_of_shards": 3,
                                  "number_of_replicas": 0,
                                  "blocks": {
                                    "read_only_allow_delete": "false"
                                  }
                                }
                              },
                              "mappings": {
                                "properties": {
                                  "title": {
                                    "type": "text"
                                  }
                                }
                              }
                            }
                        """,
                                XContentType.JSON
                            )
                        }
                    }
                }
                blockWithOutput {
                    // all functions on the asyncRepo are of course suspend so we
                    // need to run them in a co-routine scope
                    runBlocking {
                        // all of these use suspend functions
                        asyncRepo.index("thing1", Thing("The first thing"))
                        // this uses the `AsyncBulkIndexingSession`, which uses the new
                        // `Flow` API underneath.
                        asyncRepo.bulk {
                            for (i in 2.rangeTo(10)) {
                                index("thing_$i", Thing("thing $i"))
                            }
                        }
                        asyncRepo.refresh()
                        val count = asyncRepo.count { }
                        println("indexed $count items")
                    }
                }

                +"""
                ## Asynchronous search
                
                The search API is very similar; except for the returned ${AsyncSearchResults::class.simpleName}. The 
                results make use of the `Flow` api in the Kotlin Co-Routines library.
            """
                blockWithOutput {
                    runBlocking {
                        val results = asyncRepo.search(scrolling = true) {
                            dsl {
                                query = matchAll()
                            }
                        }

                        // hits is a Flow<Thing>
                        println("Hits: ${results.mappedHits.count()}")
                    }
                }
            }
        }
    }
}
