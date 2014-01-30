/**
 * Copyright (c) 2011 Metropolitan Transportation Authority
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.onebusaway.voicexml.impl;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.onebusaway.nyc.geocoder.service.NycGeocoderResult;
import org.onebusaway.nyc.presentation.impl.search.AbstractSearchResultFactoryImpl;
import org.onebusaway.nyc.presentation.model.SearchResult;
import org.onebusaway.nyc.presentation.service.realtime.RealtimeService;
import org.onebusaway.nyc.presentation.service.search.SearchResultFactory;
import org.onebusaway.nyc.transit_data.services.NycTransitDataService;
import org.onebusaway.nyc.transit_data_federation.siri.SiriDistanceExtension;
import org.onebusaway.nyc.transit_data_federation.siri.SiriExtensionWrapper;
import org.onebusaway.nyc.util.configuration.ConfigurationService;
import org.onebusaway.voicexml.model.RouteAtStop;
import org.onebusaway.voicexml.model.RouteDirection;
import org.onebusaway.voicexml.model.StopResult;
import org.onebusaway.transit_data.model.NameBean;
import org.onebusaway.transit_data.model.RouteBean;
import org.onebusaway.transit_data.model.StopBean;
import org.onebusaway.transit_data.model.StopGroupBean;
import org.onebusaway.transit_data.model.StopGroupingBean;
import org.onebusaway.transit_data.model.StopsForRouteBean;
import org.onebusaway.transit_data.model.service_alerts.ServiceAlertBean;

import uk.org.siri.siri.MonitoredCallStructure;
import uk.org.siri.siri.MonitoredStopVisitStructure;
import uk.org.siri.siri.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri.NaturalLanguageStringStructure;

public class SearchResultFactoryImpl extends AbstractSearchResultFactoryImpl implements SearchResultFactory {

  private ConfigurationService _configurationService;

  private RealtimeService _realtimeService;

  private NycTransitDataService _nycTransitDataService;

  public SearchResultFactoryImpl(NycTransitDataService nycTransitDataService,
      RealtimeService realtimeService, ConfigurationService configurationService) {
    _nycTransitDataService = nycTransitDataService;
    _realtimeService = realtimeService;
    _configurationService = configurationService;
  }


  @Override
  public SearchResult getStopResult(StopBean stopBean, Set<RouteBean> routeFilter) {
    List<RouteAtStop> routesWithArrivals = new ArrayList<RouteAtStop>();
    List<RouteAtStop> routesWithNoVehiclesEnRoute = new ArrayList<RouteAtStop>();
    List<RouteAtStop> routesWithNoScheduledService = new ArrayList<RouteAtStop>();
    List<RouteBean> filteredRoutes = new ArrayList<RouteBean>();
    
    Set<String> serviceAlertDescriptions = new HashSet<String>();

    for (RouteBean routeBean : stopBean.getRoutes()) {
      if (routeFilter != null && !routeFilter.isEmpty()
          && !routeFilter.contains(routeBean)) {
        filteredRoutes.add(routeBean);
        continue;
      }

      StopsForRouteBean stopsForRoute = _nycTransitDataService.getStopsForRoute(routeBean.getId());

      List<RouteDirection> directions = new ArrayList<RouteDirection>();
      List<StopGroupingBean> stopGroupings = stopsForRoute.getStopGroupings();
      for (StopGroupingBean stopGroupingBean : stopGroupings) {
        for (StopGroupBean stopGroupBean : stopGroupingBean.getStopGroups()) {
          NameBean name = stopGroupBean.getName();
          String type = name.getType();

          if (!type.equals("destination"))
            continue;

          // filter out route directions that don't stop at this stop
          if (!stopGroupBean.getStopIds().contains(stopBean.getId()))
            continue;

          // arrivals in this direction
          Map<String, List<String>> arrivalsForRouteAndDirection = getDisplayStringsByHeadsignForStopAndRouteAndDirection(
              stopBean, routeBean, stopGroupBean);

          // service alerts for this route + direction
          List<ServiceAlertBean> serviceAlertBeans = _realtimeService.getServiceAlertsForRouteAndDirection(
              routeBean.getId(), stopGroupBean.getId());
          populateServiceAlerts(serviceAlertDescriptions, serviceAlertBeans);

          // service in this direction
          Boolean hasUpcomingScheduledService = _nycTransitDataService.stopHasUpcomingScheduledService(
              System.currentTimeMillis(), stopBean.getId(), routeBean.getId(),
              stopGroupBean.getId());

          // if there are buses on route, always have "scheduled service"
          if (!arrivalsForRouteAndDirection.isEmpty()) {
            hasUpcomingScheduledService = true;
          }

          if(arrivalsForRouteAndDirection.isEmpty()) {
            directions.add(new RouteDirection(stopGroupBean.getName().getName(), stopGroupBean, null, 
                hasUpcomingScheduledService, Collections.<String>emptyList()));
          } else {          
            for (Map.Entry<String,List<String>> entry : arrivalsForRouteAndDirection.entrySet()) {
              directions.add(new RouteDirection(entry.getKey(), stopGroupBean, null, 
                 hasUpcomingScheduledService, entry.getValue()));
            }
          }
        }
      }

      RouteAtStop routeAtStop = new RouteAtStop(routeBean, directions,
          serviceAlertDescriptions);

      // Keep track of service and no service per route. If at least one
      // direction per route meets some criteria, break because that route is handled.
      for (RouteDirection direction : routeAtStop.getDirections()) {
        if (direction.getHasUpcomingScheduledService() == false && direction.getDistanceAways().isEmpty()) {
          routesWithNoScheduledService.add(routeAtStop);
          break;
        } else {
          if (!direction.getDistanceAways().isEmpty()) {
            routesWithArrivals.add(routeAtStop);
            break;
          } else {
            routesWithNoVehiclesEnRoute.add(routeAtStop);
            break;
          }
        }
      }
    }

    return new StopResult(stopBean, routesWithArrivals,
        routesWithNoVehiclesEnRoute, routesWithNoScheduledService, filteredRoutes, serviceAlertDescriptions);
  }
  
  private Map<String, List<String>> getDisplayStringsByHeadsignForStopAndRouteAndDirection(
      StopBean stopBean, RouteBean routeBean, StopGroupBean stopGroupBean) {
    
    Map<String, List<String>> results = new HashMap<String, List<String>>();

    // stop visits
    List<MonitoredStopVisitStructure> visitList = _realtimeService.getMonitoredStopVisitsForStop(
        stopBean.getId(), 0);

    for (MonitoredStopVisitStructure visit : visitList) {
      String routeId = visit.getMonitoredVehicleJourney().getLineRef().getValue();
      if (!routeBean.getId().equals(routeId))
        continue;

      String directionId = visit.getMonitoredVehicleJourney().getDirectionRef().getValue();
      if (!stopGroupBean.getId().equals(directionId))
        continue;

      // on detour?
      MonitoredCallStructure monitoredCall = visit.getMonitoredVehicleJourney().getMonitoredCall();
      if (monitoredCall == null)
        continue;

      if (!results.containsKey(visit.getMonitoredVehicleJourney().getDestinationName().getValue()))
        results.put(visit.getMonitoredVehicleJourney().getDestinationName().getValue(), new ArrayList<String>());
      
      if(results.get(visit.getMonitoredVehicleJourney().getDestinationName().getValue()).size() >= 3)
        continue;
      
      String distance = getPresentableDistance(visit.getMonitoredVehicleJourney(),
    		visit.getRecordedAtTime().getTime(), true);
    	  
      String timePrediction = getPresentableTime(visit.getMonitoredVehicleJourney(),
    	 	visit.getRecordedAtTime().getTime(), true);

      if(timePrediction != null) {
        results.get(visit.getMonitoredVehicleJourney().getDestinationName().getValue()).add(timePrediction);
      } else {
        results.get(visit.getMonitoredVehicleJourney().getDestinationName().getValue()).add(distance);
      }
    }

    return results;
  }

  private String getPresentableTime(
		  MonitoredVehicleJourneyStructure journey, long updateTime,
	      boolean isStopContext) {

	  NaturalLanguageStringStructure progressStatus = journey.getProgressStatus();
	  MonitoredCallStructure monitoredCall = journey.getMonitoredCall();
	  
	  if(!isStopContext) {
		  return null;
	  }
	  
	  int staleTimeout = _configurationService.getConfigurationValueAsInteger("display.staleTimeout", 120);
	  long age = (System.currentTimeMillis() - updateTime) / 1000;

	  if (age > staleTimeout) {
		  return null;
	  }
	  
	  if(monitoredCall.getExpectedArrivalTime() != null) {
		  long predictedArrival = monitoredCall.getExpectedArrivalTime().getTime();

		  SiriExtensionWrapper wrapper = (SiriExtensionWrapper) monitoredCall.getExtensions().getAny();
		  SiriDistanceExtension distanceExtension = wrapper.getDistances();
		  String distance = distanceExtension.getPresentableDistance();
		  
		  double minutes = Math.floor((predictedArrival - updateTime) / 60 / 1000);
		  String timeString = minutes + " minute" + ((Math.abs(minutes) != 1) ? "s" : "");
				  
		  if(progressStatus != null && progressStatus.getValue().contains("prevTrip")) {
		    	return timeString;
		  } else {
		    	return timeString + ", " + distance;
		  }
	  }
	  
	  return null;
  }	  
  
  private String getPresentableDistance(
      MonitoredVehicleJourneyStructure journey, long updateTime,
      boolean isStopContext) {
    MonitoredCallStructure monitoredCall = journey.getMonitoredCall();
    SiriExtensionWrapper wrapper = (SiriExtensionWrapper) monitoredCall.getExtensions().getAny();
    SiriDistanceExtension distanceExtension = wrapper.getDistances();

    String message = "";
    String distance = "<emphasis>" + distanceExtension.getPresentableDistance() + "</emphasis>";

    NaturalLanguageStringStructure progressStatus = journey.getProgressStatus();

    // at terminal label only appears in stop results
    if (isStopContext && progressStatus != null
            && progressStatus.getValue().contains("layover")) {
   
    	if(journey.getOriginAimedDepartureTime() != null) {
        	DateFormat formatter = DateFormat.getTimeInstance(DateFormat.SHORT);
        	
        	if(journey.getOriginAimedDepartureTime().getTime() < new Date().getTime()) {
        		message += "at terminal";
        	} else {    			
        		message += "at terminal, scheduled to depart " + formatter.format(journey.getOriginAimedDepartureTime());
        	}
        } else {
        	message += "at terminal";
        }
    } else if (isStopContext && progressStatus != null
        && progressStatus.getValue().contains("prevTrip")) {
    	message += "+ scheduled layover at terminal";
    }
    	
    int staleTimeout = _configurationService.getConfigurationValueAsInteger(
        "display.staleTimeout", 120);
    long age = (System.currentTimeMillis() - updateTime) / 1000;

    if (age > staleTimeout) {
      if (message.length() > 0) {
        message += ", ";
      }

      message += "old data";
    }

    if (message.length() > 0)
      return distance + " (" + message + ")";
    else
      return distance;
  }

    @Override
    public SearchResult getRouteResult(RouteBean rb) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public SearchResult getRouteResultForRegion(RouteBean rb) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public SearchResult getGeocoderResult(NycGeocoderResult ngr, Set<RouteBean> set) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
