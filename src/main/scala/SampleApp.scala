import com.twitter.finagle.Redis
import com.twitter.finagle.redis
import com.twitter.finagle.redis.util._
import com.twitter.util.{Await, Future}

object SampleApp extends App {

	val serviceFactory = Redis.newClient("localhost:6379")

	val sample = serviceFactory() flatMap {
		service =>
			val redisClient = redis.Client(service)
			SampleCommands.setSample(redisClient) flatMap {
				setResult =>
					SampleCommands.getSample(redisClient) flatMap {
						getResult =>
							println(getResult)
							service.close()
							Future.value(1)
					}
			}
	}

	sample onSuccess { x =>
		println("OK")
	}

	sample onFailure { ex =>
		println("Oops")
	}

	Await.ready(sample)



}