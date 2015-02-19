import com.twitter.finagle.redis
import com.twitter.finagle.redis.util.StringToChannelBuffer
import com.twitter.finagle.redis.util.CBToString
import com.twitter.util.Future

trait SimpleCommands {

	// The SET command is rather simple, it just converts the key and value arguments
	// into a string channel buffers that are actually the types needed at finagle-redis
	// client	
	def simpleSet(redisClient: redis.Client, key: String, value: String) = {
		val k = StringToChannelBuffer(key)
		val v = StringToChannelBuffer(value)
		redisClient.set(k, v)
	}

  // The GET command is more worked since it returns a future channel buffer that
  // needs to be composed using flatMap and reconstruct the converted future that
  // on top of that is Option since the value can be not found and this should be
  // properly communicated
	def simpleGet(redisClient: redis.Client, key: String): Future[Option[String]] = {
		val k = StringToChannelBuffer(key)
		val v = redisClient.get(k)
		v flatMap {
			case Some(v) =>
				val value = CBToString(v)
				Future.value(Some(value))
			case _ =>
				Future.value(None)
		}
	}

	def simpleQueueListener(redisClient: redis.Client, queueKey: String) = {
		println("Queue listener opened for the list with key " + queueKey)
		def loop: Future[Nothing] = {
			val k = StringToChannelBuffer(queueKey)
			// A blocking left pop (BLPOP) can be used more efficiently here when
			// finagle-redis supports it. http://redis.io/commands/blpop
			redisClient.lPop(k) flatMap {
				case Some(v) =>
					val value = CBToString(v)
					print(value + " ")
					loop
				case _ =>
					// A short time pause should be attempted for the empty reads loop case
					// for not saturating the network bandwidth nor the CPU as a workaround
					// until the BLPOP command is available for finagle-redis.
					loop
			}
		}
		loop
	}

	def simpleQueuePush(redisClient: redis.Client, queueKey: String, value: String) = {
		val k = StringToChannelBuffer(queueKey)
		val v = StringToChannelBuffer(value)
		redisClient.rPush(k, List(v))
	}

}
