package gr.papadogiannis.stefanos.server.masters

import gr.papadogiannis.stefanos.server.masters.Master.{FinalResponse, RequestTrackMapper, RequestTrackReducer}
import gr.papadogiannis.stefanos.server.reducers.ReducersGroup.{CalculateReduction, RespondAllReduceResults}
import gr.papadogiannis.stefanos.server.mappers.MappersGroup.RespondAllMapResults
import com.google.maps.model.{DirectionsLeg, DirectionsResult, DirectionsRoute}
import gr.papadogiannis.stefanos.server.servers.Server.CalculateDirections
import gr.papadogiannis.stefanos.server.models.{GeoPoint, GeoPointPair}
import gr.papadogiannis.stefanos.server.Main.CreateInfrastracture
import gr.papadogiannis.stefanos.server.reducers.ReducersGroup
import gr.papadogiannis.stefanos.server.mappers.MappersGroup
import akka.actor.{Actor, ActorLogging, ActorRef, Props}

object Master {

  def props: Props = Props(new Master)

  final case class RequestTrackMapper(str: String)

  final case class RequestTrackReducer(a: String)

  case class FinalResponse(request: ReducersGroup.CalculateReduction, results: DirectionsResult)

}

class Master
  extends Actor
    with ActorLogging {

  var mappersGroupActor: ActorRef = _

  var reducersGroupActor: ActorRef = _

  var requester: ActorRef = _

  override def preStart(): Unit = log.info("MasterImpl started")

  override def postStop(): Unit = log.info("MasterImpl stopped")

  override def receive: Receive = {
    case CreateInfrastracture =>
      log.info("Creating reducers group actor.")
      reducersGroupActor = context.actorOf(ReducersGroup.props(mappersGroupActor, this.self))
      reducersGroupActor ! RequestTrackReducer("moscow")
      log.info("Creating mappers group actor.")
      mappersGroupActor = context.actorOf(MappersGroup.props(reducersGroupActor, this.self))
      context.watch(mappersGroupActor)
      mappersGroupActor ! RequestTrackMapper("havana")
      mappersGroupActor ! RequestTrackMapper("saoPaolo")
      mappersGroupActor ! RequestTrackMapper("athens")
      mappersGroupActor ! RequestTrackMapper("jamaica")
    case request@CalculateDirections =>
      requester = sender()
      mappersGroupActor forward request
    case RespondAllMapResults(request, results) =>
      val merged = results.filter(x => x._2.isInstanceOf[MappersGroup.ConcreteResult])
        .foldLeft[List[Map[GeoPointPair, DirectionsResult]]](List.empty[Map[GeoPointPair, DirectionsResult]])((x, y) =>
          x ++ y._2.asInstanceOf[MappersGroup.ConcreteResult].value)
      reducersGroupActor ! CalculateReduction(request.requestId, merged)
    case RespondAllReduceResults(request, results) =>
      val merged = results.filter(x => x._2.isInstanceOf[ReducersGroup.ConcreteResult])
        .foldLeft[Map[GeoPointPair, List[DirectionsResult]]](Map.empty[GeoPointPair, List[DirectionsResult]])((x, y) =>
          x + (y._1.asInstanceOf[GeoPointPair] -> y._2.asInstanceOf[ReducersGroup.ConcreteResult].valueOption.asInstanceOf[List[DirectionsResult]]))
      val value = calculateEuclideanMin(merged)
      requester ! FinalResponse(request, value)
  }

  import com.google.maps.model.DirectionsResult

  import java.util

  var startLatitude: Double = 30.0

  var startLongitude: Double = 23.0

  var endLatitude: Double = 29.0

  var endLongitude: Double = 43.0

  def calculateEuclideanMin(result: Map[GeoPointPair, List[DirectionsResult]]): DirectionsResult =
    if (result.isEmpty) null
    else {
      var min: Tuple2[GeoPointPair, List[DirectionsResult]] = null
      val startGeoPoint = new GeoPoint(startLatitude, startLongitude)
      val endGeoPoint = new GeoPoint(endLatitude, endLongitude)
      for ((key, value) <- result) {
        if (min == null || min._1.getStartGeoPoint.euclideanDistance(startGeoPoint) + min._1.getEndGeoPoint.euclideanDistance(endGeoPoint) >
          key.getStartGeoPoint.euclideanDistance(startGeoPoint) + key.getEndGeoPoint.euclideanDistance(endGeoPoint)) min = (key, value)
      }
      val minDirectionsResultList =
        if (min != null)
          min._2
        else
          null
      if (minDirectionsResultList != null && minDirectionsResultList.nonEmpty) {
        var minDirectionsResult: DirectionsResult = null
        for (directionsResult <- minDirectionsResultList) {
          if (minDirectionsResult == null)
            minDirectionsResult = directionsResult
          else {
            val totalDurationOfMin = Array(0L)
            val totalDurationOfIteratee = Array(0L)
            util.Arrays.stream(minDirectionsResult.routes).forEach((x: DirectionsRoute) => {
              def foo(x: DirectionsRoute) = util.Arrays.stream(x.legs).forEach((y: DirectionsLeg) => {
                def foo(y: DirectionsLeg) = {
                  totalDurationOfMin(0) += y.duration.inSeconds
                }

                foo(y)
              })

              foo(x)
            })
            util.Arrays.stream(directionsResult.routes).forEach((x: DirectionsRoute) => {
              def foo(x: DirectionsRoute) = util.Arrays.stream(x.legs).forEach((y: DirectionsLeg) => {
                def foo(y: DirectionsLeg) = {
                  totalDurationOfIteratee(0) += y.duration.inSeconds
                }

                foo(y)
              })

              foo(x)
            })
            if (totalDurationOfIteratee(0) < totalDurationOfMin(0))
              minDirectionsResult = directionsResult
          }
        }
        minDirectionsResult
      }
      else null
    }

}
