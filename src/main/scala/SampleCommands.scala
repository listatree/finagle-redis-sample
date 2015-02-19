import com.twitter.finagle.redis
import com.twitter.finagle.redis.util.StringToChannelBuffer
import com.twitter.util.Future

object SampleCommands {
	
	def setSample(redisClient: redis.Client) = {
		val key = StringToChannelBuffer("hello")
		val value = StringToChannelBuffer("world")
		redisClient.set(key, value)
	}

	def getSample(redisClient: redis.Client) = {
		val key = StringToChannelBuffer("hello")
		redisClient.get(key)
	}

	def queueLoopSample(redisClient: redis.Client) = {
		def loop: Future[Nothing] = {
			val key = StringToChannelBuffer("queue:sample")
			redisClient.lPop(key) flatMap {
				case Some(value) =>
					println(value)
					loop
				case _ =>
					loop
			}
		}
		loop
	}

	def queuePushSample(redisClient: redis.Client) = {
		val key = StringToChannelBuffer("queue:sample")
		val value = StringToChannelBuffer("hello world")
		redisClient.lPush(key, List(value))
	}

}
