/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.polettif.publicTransitMapping.mapping;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.*;
import playground.polettif.publicTransitMapping.config.PublicTransitMappingConfigGroup;
import playground.polettif.publicTransitMapping.mapping.pseudoPTRouter.LinkCandidate;
import playground.polettif.publicTransitMapping.mapping.pseudoPTRouter.PseudoRouteStop;
import playground.polettif.publicTransitMapping.tools.CoordTools;
import playground.polettif.publicTransitMapping.tools.NetworkTools;
import playground.polettif.publicTransitMapping.tools.ScheduleTools;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides some static tools for PTMapper.
 *
 * @author polettif
 */
public class PTMapperUtils {

	protected static Logger log = Logger.getLogger(PTMapperUtils.class);
	private static String suffixChildStopFacilities = ".link:";
	private static String suffixChildStopFacilitiesRegex = "[.]link:";

	public static void setSuffixChildStopFacilities(String suffix, String suffixRegex) {
		suffixChildStopFacilities = suffix;
		suffixChildStopFacilitiesRegex = suffixRegex;
	}


	/**
	 * Generates link candidates for all stopFacilities. For stop facilities where
	 * no link can be found within nodeSearchRadius an artificial node and loop
	 * link (from and to the new node) is created {@link NetworkTools#createArtificialStopFacilityLink(TransitStopFacility, Network, String, double)}.
	 * For each link candiate a child stop facility is generated and referenced to
	 * the link. Link candidates for different modes with the same link use the same
	 * child stop facility. Child stop facilities are not created and added to the schedule!
	 * If a stop facility already has a referenced link, this link is used as the only link
	 * candidate.<p/>
	 *
	 * @param schedule with stopFacilities, not modified.
	 * @param network  the network where link candidates should be looked for, is modified
	 *                 for stop facilities without a link nearby
	 * @param config   containing the modeRoutingAssignments and params for link searching
	 * @return a map with all link candidates for a stop facilitiy and
	 * the scheduleTransportMode as top level key.
	 */
	public static Map<String, Map<TransitStopFacility, Set<LinkCandidate>>> generateModeLinkCandidates(TransitSchedule schedule, Network network, PublicTransitMappingConfigGroup config) {
		Map<String, Map<TransitStopFacility, Set<LinkCandidate>>> tree = new HashMap<>();

		/**
		 * get closest links for each stop facility (separated by mode)
		 */
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				for(TransitRouteStop transitRouteStop : transitRoute.getStops()) {
					String scheduleTransportMode = transitRoute.getTransportMode();
					TransitStopFacility stopFacility = transitRouteStop.getStopFacility();

					Set<LinkCandidate> modeLinkCandidates = MapUtils.getSet(stopFacility, MapUtils.getMap(scheduleTransportMode, tree));

					// if no link candidates for the current stop and mode have been generated
					if(modeLinkCandidates.size() == 0) {

						// if stop facilty already has a referenced link
						if(stopFacility.getLinkId() != null) {
							modeLinkCandidates.add(new LinkCandidate(network.getLinks().get(stopFacility.getLinkId()), stopFacility));
						} else {
							// limits number of links, for all links within search radius use networkTools.findClosestLinks()
							List<Link> closestLinks = findClosestLinksByScheduleMode(network, stopFacility.getCoord(), scheduleTransportMode, config);

							// if no close links are nearby, a loop link is created and referenced to the facility.
							if(closestLinks.size() == 0) {
								Link loopLink = NetworkTools.createArtificialStopFacilityLink(stopFacility, network, config.getPrefixArtificial(), config.getFreespeedArtificial());
								closestLinks.add(loopLink);
							}

							/**
							 * generate a LinkCandidate for each close link
							 */
							for(Link link : closestLinks) {
								modeLinkCandidates.add(new LinkCandidate(link, stopFacility));
							}
						}
					}
				}
			}
		}
		return tree;
	}

	/**
	 * Using the pseudo schedule, the facilities in the stop sequences of the actual schedule
	 * are replaced with child facilities. Child facilities are created in this step and
	 * added to the schedule.
	 *
	 * @param schedule                where the facilities should be replaced
	 * @param pseudoSchedule          defines the actual sequence of pseudoRouteStops
	 * @param childStopFacilitySuffix what suffix the child facility should get in the id
	 */
	public static void createAndReplaceFacilities(TransitSchedule schedule, Map<TransitLine, Map<TransitRoute, List<PseudoRouteStop>>> pseudoSchedule, String childStopFacilitySuffix) {
		log.info("Replacing parent StopFacilities with child StopFacilities...");
		TransitScheduleFactory scheduleFactory = schedule.getFactory();
		List<Tuple<TransitLine, TransitRoute>> newRoutes = new ArrayList<>();

		for(Map.Entry<TransitLine, Map<TransitRoute, List<PseudoRouteStop>>> lineEntry : pseudoSchedule.entrySet()) {
			for(Map.Entry<TransitRoute, List<PseudoRouteStop>> routeEntry : lineEntry.getValue().entrySet()) {

				List<PseudoRouteStop> pseudoStopSequence = routeEntry.getValue();
				List<TransitRouteStop> newStopSequence = new ArrayList<>();

				for(PseudoRouteStop pseudoStop : pseudoStopSequence) {
					Id<TransitStopFacility> childStopFacilityId = Id.create(pseudoStop.getParentStopFacilityId() + childStopFacilitySuffix + pseudoStop.getLinkIdStr(), TransitStopFacility.class);

					// if child stop facility for this link has not yet been generated
					if(!schedule.getFacilities().containsKey(childStopFacilityId)) {
						TransitStopFacility newFacility = scheduleFactory.createTransitStopFacility(
								Id.create(childStopFacilityId, TransitStopFacility.class),
								pseudoStop.getCoord(),
								pseudoStop.getIsBlockingLane()
						);
						newFacility.setLinkId(Id.createLinkId(pseudoStop.getLinkIdStr()));
						newFacility.setName(pseudoStop.getFacilityName());
						newFacility.setStopPostAreaId(pseudoStop.getStopPostAreaId());
						schedule.addStopFacility(newFacility);
					}

					// create new TransitRouteStop and add it to the newStopSequence
					TransitRouteStop newTransitRouteStop = scheduleFactory.createTransitRouteStop(
							schedule.getFacilities().get(childStopFacilityId),
							pseudoStop.getArrivalOffset(),
							pseudoStop.getDepartureOffset());
					newTransitRouteStop.setAwaitDepartureTime(pseudoStop.isAwaitDepartureTime());
					newStopSequence.add(newTransitRouteStop);
				}

				// create a new transitRoute
				TransitRoute newRoute = scheduleFactory.createTransitRoute(routeEntry.getKey().getId(), null, newStopSequence, routeEntry.getKey().getTransportMode());

				// add departures
				routeEntry.getKey().getDepartures().values().forEach(newRoute::addDeparture);

				// remove the old route
				schedule.getTransitLines().get(lineEntry.getKey().getId()).removeRoute(routeEntry.getKey());

				// add new route to container
				newRoutes.add(new Tuple<>(lineEntry.getKey(), newRoute));
			}
		}

		// add transit lines and routes again
		for(Tuple<TransitLine, TransitRoute> entry : newRoutes) {
			schedule.getTransitLines().get(entry.getFirst().getId()).addRoute(entry.getSecond());
		}
	}


	/**
	 * @return the linkIds of the links in path
	 */
	public static List<Id<Link>> getLinkIdsFromPath(LeastCostPathCalculator.Path path) {
//		List<Id<Link>> list = new ArrayList<>();
//		for(Link link : path.links) {
//			list.add(link.getId());
//		}
		return path.links.stream().map(Link::getId).collect(Collectors.toList());
	}


	/**
	 * If link candidates have the same link for boths stop facilities, the link candidate is
	 * assigned to the stop facility that is closer (i.e. removed from the other set).
	 */
	public static void separateLinkCandidates(Set<LinkCandidate> linkCandidatesCurrent, Set<LinkCandidate> linkCandidatesNext) {
		Set<LinkCandidate> removeFromCurrent = new HashSet<>();
		Set<LinkCandidate> removeFromNext = new HashSet<>();

		for(LinkCandidate lcCurrent : linkCandidatesCurrent) {
			for(LinkCandidate lcNext : linkCandidatesNext) {
				if(lcCurrent.getLinkIdStr().equals(lcNext.getLinkIdStr())) {
					if(lcCurrent.getStopFacilityDistance() > lcNext.getStopFacilityDistance()) {
						removeFromCurrent.add(lcCurrent);
					} else {
						removeFromNext.add(lcNext);
					}
				}
			}
		}
		removeFromCurrent.forEach(linkCandidatesCurrent::remove);
		removeFromNext.forEach(linkCandidatesNext::remove);
	}

	/**
	 * Checks for each child stop facility if the link before or after is closer to the facility
	 * than its referenced link. If so, the child stop facility is replaced with the one closer
	 * to the facility coordinates.<p/>
	 * <p/>
	 * Since all links are part of the route, no rerouting has to be done.
	 *
	 * @param schedule
	 * @param network
	 */
	public static void pullChildStopFacilitiesTogether(TransitSchedule schedule, Network network) {
		log.info("Concentrating child stop facilities...");
		for(TransitLine line : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : line.getRoutes().values()) {
				if(transitRoute.getRoute() != null) {
					List<TransitRouteStop> routeStops = transitRoute.getStops();
					Iterator<TransitRouteStop> stopsIterator = routeStops.iterator();

					List<Id<Link>> linkIdList = ScheduleTools.getLinkIds(transitRoute);
					List<Link> linkList = NetworkTools.getLinksFromIds(network, linkIdList);

					TransitRouteStop currentStop = stopsIterator.next();
					Id<Link> closerLinkBefore = useCloserRefLinkForChildStopFacility(schedule, network, transitRoute, currentStop.getStopFacility(), linkList.get(0).getFromNode().getInLinks().values());
					if(closerLinkBefore != null) {
						linkIdList.add(0, closerLinkBefore);
					}
					currentStop = stopsIterator.next();

					for(int i = 1; i < linkList.size()-1; i++) {

						if(linkList.get(i).getId().equals(currentStop.getStopFacility().getLinkId())) {
							Set<Link> testSet = new HashSet<>();
							testSet.add(linkList.get(i));
							testSet.add(linkList.get(i-1));
							testSet.add(linkList.get(i+1));
							useCloserRefLinkForChildStopFacility(schedule, network, transitRoute, currentStop.getStopFacility(), testSet);
							// setCloserStopFacility(schedule, transitRoute, currentStop.getStopFacility(), linkList.get(i), , linkList.get(i + 1));
							if(stopsIterator.hasNext()) {
								currentStop = stopsIterator.next();
							}
						}
					}
					currentStop = routeStops.get(routeStops.size() - 1);
					Id<Link> closerLinkAfter = useCloserRefLinkForChildStopFacility(schedule, network, transitRoute, currentStop.getStopFacility(), linkList.get(linkList.size() - 1).getToNode().getOutLinks().values());
					if(closerLinkAfter != null) {
						linkIdList.add(closerLinkAfter);
					}
					transitRoute.setRoute(RouteUtils.createNetworkRoute(linkIdList, network));
				}
			}
		}
	}

	/**
	 * If a link of <tt>comparingLinks</tt> is closer to the stop facility than
	 * its currently referenced link, the closest link is used.
	 * @param schedule
	 * @param network
	 * @param transitRoute
	 * @param stopFacility
	 * @param comparingLinks
	 * @return
	 */
	private static Id<Link> useCloserRefLinkForChildStopFacility(TransitSchedule schedule, Network network, TransitRoute transitRoute, TransitStopFacility stopFacility, Collection<? extends Link> comparingLinks) {
		// check if previous link is closer to stop facility
		double minDist = CoordTools.distanceStopFacilityToLink(stopFacility, network.getLinks().get(stopFacility.getLinkId()));
		Link minLink = null;

		for(Link comparingLink : comparingLinks) {
			double distCompare = CoordTools.distanceStopFacilityToLink(stopFacility, comparingLink);
			if(distCompare < minDist) {
				minDist = distCompare;
				minLink = comparingLink;
			}
		}

		if(minLink != null) {
			TransitStopFacility newChildStopFacility;
			String[] split = stopFacility.getId().toString().split(suffixChildStopFacilitiesRegex);
			Id<TransitStopFacility> newChildStopFacilityId = Id.create(split[0] + suffixChildStopFacilities + minLink.getId(), TransitStopFacility.class);
			if(schedule.getFacilities().containsKey(newChildStopFacilityId)) {
				newChildStopFacility = schedule.getFacilities().get(newChildStopFacilityId);
			} else {
				newChildStopFacility = schedule.getFactory().createTransitStopFacility(newChildStopFacilityId, stopFacility.getCoord(), false);
				newChildStopFacility.setName(stopFacility.getName());
				newChildStopFacility.setStopPostAreaId(stopFacility.getStopPostAreaId());
				newChildStopFacility.setLinkId(minLink.getId());
				schedule.addStopFacility(newChildStopFacility);
			}
			transitRoute.getStop(stopFacility).setStopFacility(newChildStopFacility);
			return minLink.getId();
		} else {
			return null;
		}
	}

	/**
	 * Looks for nodes within search radius of coord (using {@link NetworkImpl#getNearestNodes(Coord, double)},
	 * fetches all in- and outlinks and sorts them ascending by their
	 * distance to the coordiantes given. Only returns links with the allowed
	 * networkTransportMode for the input scheduleTransportMode (defined in
	 * config). Returns maxNLinks or all links within maxLinkDistance (whichever
	 * is reached earlier).
	 * <p/>
	 * <p/>
	 * Distance Link-Coordinate is calculated via  in {@link org.matsim.core.utils.geometry.CoordUtils#distancePointLinesegment(Coord, Coord, Coord)}).
	 *
	 * @param network               A network (needs to be NetworkImpl
	 *                              for {@link NetworkImpl#getNearestNodes(Coord, double)}
	 * @param coord                 the coordinate from which the closest links
	 *                              are searched
	 * @param scheduleTransportMode the transport mode of the "current" transitRoute.
	 *                              The config should define which networkTransportModes
	 *                              are allowed for this scheduleMode
	 * @param config                The config defining maxNnodes, search radius etc.
	 * @return a Set of the closest links
	 */
	public static List<Link> findClosestLinksByScheduleMode(Network network, Coord coord, String
			scheduleTransportMode, PublicTransitMappingConfigGroup config) {
		return NetworkTools.findClosestLinks(
				network,
				coord,
				config.getNodeSearchRadius(), config.getMaxNClosestLinks(), config.getLinkDistanceTolerance(), config.getModeRoutingAssignment().get(scheduleTransportMode),
				config.getMaxLinkCandidateDistance()
		);
	}
}