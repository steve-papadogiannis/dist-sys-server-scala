import akka.actor.{Actor, ActorLogging, Props}

object AndroidServer {
  def props: Props = Props(new  AndroidServer)
}

class AndroidServer extends Actor with ActorLogging {
  override def preStart(): Unit = log.info("Android Server started")
  override def postStop(): Unit = log.info("Android Server stopped")
  override def receive: Receive = {
    case CreateInfrastracture =>
      val master = context.actorOf(MasterImpl.props)
      master ! CreateInfrastracture
  }
}
