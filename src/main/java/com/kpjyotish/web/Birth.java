package com.kpjyotish.web;

import com.kpjyotish.config.ApiException;
import com.kpjyotish.places.PlaceIndex;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Validates birth details (date, time with seconds, place) and resolves coordinates + exact UTC offset. */
@Component
public class Birth {
    public record Data(String dob, String tob, String city, double lat, double lon, double tz, String tzName) {}

    private final PlaceIndex places;
    public Birth(PlaceIndex places) { this.places = places; }

    static boolean has(Object o) { return o != null && !String.valueOf(o).isBlank(); }

    public Data resolve(Map<String, Object> b) {
        String dob = PublicApi.clean(b.get("dob"), 10), tob = PublicApi.clean(b.get("tob"), 8);
        if (tob.matches("\\d{2}:\\d{2}")) tob += ":00";
        if (!dob.matches("\\d{4}-\\d{2}-\\d{2}") || !tob.matches("\\d{2}:\\d{2}:\\d{2}")) throw new ApiException(400, "Please enter date of birth and time of birth (HH:MM:SS).");
        if (dob.compareTo("1900-01-01") < 0 || dob.compareTo(java.time.LocalDate.now().toString()) > 0) throw new ApiException(400, "Please enter a valid date of birth.");
        Double lat = has(b.get("lat")) ? Double.valueOf(String.valueOf(b.get("lat"))) : null;
        Double lon = has(b.get("lon")) ? Double.valueOf(String.valueOf(b.get("lon"))) : null;
        String city = PublicApi.clean(b.get("city"), 200);
        PlaceIndex.Place known = null;
        if (lat == null || lon == null) {
            List<PlaceIndex.Place> l = places.searchLocal(city, 1);
            if (!l.isEmpty()) { known = l.get(0); lat = known.lat; lon = known.lon; }
        }
        if (lat == null || lon == null || Math.abs(lat) > 66 || Math.abs(lon) > 180)
            throw new ApiException(400, "Could not find the birth place. Pick it from the suggestions or enter latitude and longitude.");
        String tzName = has(b.get("tzName")) && PlaceIndex.validTz(String.valueOf(b.get("tzName"))) ? String.valueOf(b.get("tzName"))
                : known != null ? known.tzName : places.nearestTz(lat, lon);
        Double tz = null;
        if (Boolean.TRUE.equals(b.get("tzManual")) && has(b.get("tz"))) tz = Double.valueOf(String.valueOf(b.get("tz")));
        if (tz == null && PlaceIndex.validTz(tzName)) tz = PlaceIndex.offsetForLocal(tzName, dob, tob);
        if (tz == null && has(b.get("tz"))) tz = Double.valueOf(String.valueOf(b.get("tz")));
        if (tz == null || Math.abs(tz) > 14) throw new ApiException(400, "Please enter the UTC offset of the birth place.");
        return new Data(dob, tob, city.isEmpty() && known != null ? known.label : city, lat, lon, tz, tzName);
    }
}
