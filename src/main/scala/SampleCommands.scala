import com.twitter.finagle.redis
import com.twitter.finagle.redis.util.StringToChannelBuffer
import com.twitter.finagle.redis.util.CBToString
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util.Duration
import com.twitter.util.Future

trait SampleCommands {

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

  def queueListener(redisClient: redis.Client, queueKey: String) = {
    println("Queue '" + queueKey + "' opened (Dot signals queue polling, the numbers are data received at the queue)")
    val k = StringToChannelBuffer(queueKey)
    def loop: Future[Nothing] = {
      // A blocking left pop (BLPOP) command can be used more efficiently here when
      // finagle-redis supports it. http://redis.io/commands/blpop
      redisClient.lPop(k) flatMap {
        case Some(v) =>
          val value = CBToString(v)
          print(value)
          loop
        case _ =>
          print(".")
          // As a workaround until the BLPOP command and similars are available at
          // finagle-redis do a short pause while polling.
          val pollingPause = Duration.fromMilliseconds(100)
          Future.sleep(pollingPause)(DefaultTimer.twitter) flatMap {
            case _ =>
            loop
          }
      }
    }
    loop
  }

  def pushSampleDataToQueue(redisClient: redis.Client, queueKey: String) = {
    val k = StringToChannelBuffer(queueKey)
    def loop: Future[Nothing] = {
      val v = StringToChannelBuffer((math.random * 10).toInt.toString)
      redisClient.rPush(k, List(v)) flatMap {
        case _ =>
          // The push pause is optional and it's only for simulating a more easy display
          // sample in the console (you can try to vary the push pause and see what happens)
          val pushPause = Duration.fromMilliseconds((math.random * 1000).toInt)
          Future.sleep(pushPause)(DefaultTimer.twitter) flatMap {
            case _ =>
              loop
          }
      }
    }
    loop
  }

}
