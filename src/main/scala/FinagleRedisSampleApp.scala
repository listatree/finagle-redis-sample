import com.twitter.finagle.Redis
import com.twitter.finagle.redis
import com.twitter.finagle.redis.util.{CBToString, StringToChannelBuffer}
import com.twitter.util.{Await, Future}

object FinagleRedisSampleApp extends App with SimpleCommands {

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
			simpleSet(redisClient, "key:1", "one") flatMap {
				case _ => simpleGet(redisClient, "key:1")
			}
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
	// Future.join
	Await.ready(storeAndRetrieveSample)

	// Now let's create a queue sample with a listener of any value at the list with
	// the key "queue:1" and with some values pushed directly in this same sample.
	// It's important to notice that the order of the listener and the push sample is
	// not relevant and actually they are executed in paralel and Redis will warranty
	// that is the push arrives first the values will be kept at Redis until the
	// listener is active and if the listener arrives first it will loop until the
	// values are pushed, and actually it will wait forever for the pushes so
	// you can issue an RPUSH command using the key "queue:1" from any other Redis
	// client and see the values just pop here
	val queueKey = "queue:1"
	val queueListenerSample = buildRedisClient flatMap {
		redisClient =>
			simpleQueueListener(redisClient, queueKey)
	}
	val queuePushSample = buildRedisClient flatMap {
		redisClient =>
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