import com.twitter.finagle.Redis
import com.twitter.finagle.redis
import com.twitter.finagle.redis.util.{CBToString, StringToChannelBuffer}
import com.twitter.util.{Await, Future}

object FinagleRedisSampleApp extends App with SimpleCommands {

  // Let's build a redis client
  val redisClient = Redis.newRichClient("localhost:6379")

  // A simple sample that will store "one" in Redis associated with the key "key:1"
  // using the SET command and retrieve the value using the GET command. The important
  // part here is that the GET command should be attemped once the SET command is complete
  // that is why we concatenate the future of the SET command to the future of the GET
  // command using flatMap
  val storeAndRetrieveSample = simpleSet(redisClient, "key:1", "one") flatMap {
    case _ => simpleGet(redisClient, "key:1")
  }

  // Once the futures of our store and retrieve sample are set we just need to
  // use them in a onSuccess/onFailure traditional use
  storeAndRetrieveSample onSuccess {
    case Some(value) =>
      println("Value retrieved from redis: " + value)
    case _ =>
      println("ERROR: No value was read")
  } onFailure {
    case e: Exception =>
      println("ERROR: " + e.getMessage)
  }

  // We wait for store and retreive sample to complete. This is not mandatory
  // since we can make just one Await.ready call at the end of all samples using
  // Future.join.  This would be dangerous to do from within a finagle service,
  // like if we were serving responses, but is OK to do from within a script,
  // because we control the thread that we're on.
  Await.ready(storeAndRetrieveSample)

  // Now let's create a queue sample with a listener of any value at the list with
  // the key "queue:1" and with some values pushed directly in this same sample.
  // It's important to notice that depending on the way that the redis client is set up,
  // the order of the listener and the push sample may or may not matter.  Using the
  // finagle 6 APIs, ie Redis.new{Service,Client,RichClient}, it will use the pipelining
  // dispatcher, which will only make one tcp connection per redis host, and then
  // requests to the same host (in this case there's only one) will be sequenced.
  // Using the ClientBuilder API, the order is not relevant, and since there are multiple
  // tcp connections, we can't be guaranteed of which request Redis will see first.
  // Redis will guarantee that if the push arrives first the values will be kept at
  // Redis until the listener is active and if the listener arrives first it will loop
  // until the values are pushed, and actually it will wait forever for the pushes so
  // you can issue an RPUSH command using the key "queue:1" from any other Redis
  // client and see the values just pop here
  //
  // One caveat is that finagle assumes that destinations are exchangeable, so
  // if you specify "localhost:6379,localhost:6380" then pushing and then pulling
  // from the queue may end up on different machines, unless they're set up as replicas,
  // so you may perceive that you can't read your own writes.
  val queueKey = "queue:1"
  val queueListenerSample = simpleQueueListener(redisClient, queueKey)
  val queuePushSample = {
    val push200 = (1 to 200) map { i =>
      simpleQueuePush(redisClient, queueKey, i.toString)
    }
    Future.collect(push200)
  }

  // Only report on failure events since the listener is an eternal loop that
  // will only report on a failure event. This kind of listener is constructed
  // using a loop that produces a failure only future
  queueListenerSample onFailure {
    case e: Exception =>
      println("ERROR: " + e.getMessage)
  }

  // Report on the push sample success or failure
  queuePushSample onSuccess {
    case _ => println("\nPushed 200 values complete")
  } onFailure {
    case e: Exception => println("ERROR: " + e.getMessage)
  }

  // At this point we can just wait for the listener because it will last forever
  // and only stops if a failure is receives, but as an example of the Future.join
  // combinator here you have a sample
  Await.ready(Future.join(queueListenerSample, queuePushSample))
}
