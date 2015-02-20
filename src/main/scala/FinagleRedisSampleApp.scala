import com.twitter.finagle.Redis
import com.twitter.finagle.redis
import com.twitter.finagle.redis.util.{CBToString, StringToChannelBuffer}
import com.twitter.util.{Await, Future}

object FinagleRedisSampleApp extends App with SampleCommands {

  // The Redis client is built using a service factory, so let's construct one first
  val serviceFactory = Redis.newClient("localhost:6379")

  // For building a Redis client you can wrap a call to the Redis service factory
  // into a Redis base client (using redis.Client). All of this is done as a
  // concatenated futures using flatMap
  def buildRedisClient = serviceFactory.apply() flatMap {
    service =>
      val redisClient = redis.Client(service)
      Future.value(redisClient)
  }

  // A simple sample that will store "one" in Redis associated with the key "key:1"
  // using the SET command and retrieve the value using the GET command. The important
  // part here is that the GET command should be attemped once the SET command is complete
  // that is why we concatenate the future of the SET command to the future of the GET
  // command using flatMap, and by the way all of that composed with the Redis client
  // build itself using again flatMap ... that's the beauty of Future: it compose very good
  val storeAndRetrieveSample = buildRedisClient flatMap {
    redisClient =>
      println("Storing 'one' with the key 'key:1' in Redis")
      simpleSet(redisClient, "key:1", "one") flatMap {
        case _ => simpleGet(redisClient, "key:1")
      }
  }

  // Once the futures of our store and retrieve sample are set we just need to
  // use them in a onSuccess/onFailure traditional use
  storeAndRetrieveSample onSuccess {
    case Some(value) =>
      println("Value retrieved from Redis with the key 'key:1': '" + value + "'")
    case _ =>
      println("ERROR: No value was read")
  } onFailure {
    case e: Exception =>
      println("ERROR: " + e.getMessage)
  }

  // We wait for store and retreive sample to complete. This is not mandatory
  // since we can make just one Await.ready call at the end of all samples using
  // Future.join
  Await.ready(storeAndRetrieveSample)

  // Now let's create a queue listener sample that will consume any value
  // pushed with the key "queue:1".
  val queueListenerSample = buildRedisClient flatMap {
    redisClient =>
      queueListener(redisClient, "queue:1")
  }

  // The queue listener will be polling the queue with a future loop eternally that
  // will only report on failures (thus this is an on failure only future sample)
  queueListenerSample onFailure {
    case e: Exception =>
      println("Queue listener error: " + e.getMessage)
  }

  // Now let's push data to the same queue. In this sample we just keep pushing
  // in an eternal loop.
  val queuePushSample = buildRedisClient flatMap {
    redisClient =>
      pushSampleDataToQueue(redisClient, "queue:1")
  }

  // Report on the push sample only with the on failure event since the push sample
  // is also an on failure future loop.
  queuePushSample onFailure {
    case e: Exception => println("Queue push error: " + e.getMessage)
  }

  // Now wait for both the listener and the push sample, the failure of one does NOT
  // imply the failure of the other that is OK for this sample, for other cases
  // you may considere compose this futures, or use other Future combinators.
  Await.ready(Future.join(queueListenerSample, queuePushSample))

}