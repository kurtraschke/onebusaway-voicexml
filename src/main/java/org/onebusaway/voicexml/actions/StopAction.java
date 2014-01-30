/*
 * Copyright 2013 OneBusAway.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.voicexml.actions;

import org.onebusaway.voicexml.impl.SearchResultFactoryImpl;
import org.onebusaway.nyc.presentation.service.realtime.RealtimeService;
import org.onebusaway.nyc.presentation.service.search.SearchResultFactory;
import org.onebusaway.nyc.transit_data.services.NycTransitDataService;
import org.onebusaway.nyc.util.configuration.ConfigurationService;
import org.onebusaway.presentation.impl.NextActionSupport;
import org.onebusaway.transit_data.model.StopBean;
import org.onebusaway.voicexml.model.StopResult;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author kurt
 */
public class StopAction extends NextActionSupport {

    @Autowired
    private ConfigurationService _configurationService;
    @Autowired
    private NycTransitDataService _nycTransitDataService;
    @Autowired
    private RealtimeService _realtimeService;
    private String _stopId;
    private StopResult _stopResult;

    public void setStopId(String stopId) {
        this._stopId = stopId;
    }

    public String getStopId() {
        return _stopId;
    }
    
    public StopResult getStopResult() {
        return _stopResult;
    }

    public String execute() {
        StopBean stop = _nycTransitDataService.getStop(_stopId);

        SearchResultFactory factory = new SearchResultFactoryImpl(
                _nycTransitDataService, _realtimeService, _configurationService);

        _stopResult = (StopResult)factory.getStopResult(stop, null);
    
        return SUCCESS;
    }
}
