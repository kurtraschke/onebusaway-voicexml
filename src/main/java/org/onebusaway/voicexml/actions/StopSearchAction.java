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

import com.google.common.collect.Iterables;
import java.util.List;
import org.onebusaway.nyc.transit_data.services.NycTransitDataService;
import org.onebusaway.nyc.util.configuration.ConfigurationService;
import org.onebusaway.presentation.impl.NextActionSupport;
import org.onebusaway.transit_data.model.SearchQueryBean;
import org.onebusaway.transit_data.model.StopBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author kurt
 */
public class StopSearchAction extends NextActionSupport {

    @Autowired
    private ConfigurationService _configurationService;
    @Autowired
    private NycTransitDataService _nycTransitDataService;
    private String _stopCode;
    private String _stopId;
    private List<StopBean> _matches;

    public void setStopCode(String stopCode) {
        _stopCode = stopCode;
    }

    public String getStopCode() {
        return _stopCode;
    }

    public List<StopBean> getMatches() {
        return _matches;
    }

    public String getStopId() {
        return _stopId;
    }

    private SearchQueryBean makeSearchQuery(String query) {
        SearchQueryBean sqb = new SearchQueryBean();
        sqb.setQuery(query);
        sqb.setMinScoreToKeep(1.0);
        sqb.setMaxCount(9);
        return sqb;
    }

    @Override
    public String execute() {
        _matches = _nycTransitDataService.getStops(makeSearchQuery(_stopCode)).getStops();

        if (_matches.size() == 1) {
            _stopId = Iterables.getOnlyElement(_matches).getId();
            return "showStopResult";
        } else {
            return SUCCESS;
        }
    }
}
