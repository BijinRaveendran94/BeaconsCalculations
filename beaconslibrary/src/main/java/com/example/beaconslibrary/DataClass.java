package com.example.beaconslibrary;

import android.location.Location;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import com.lemmingapex.trilateration.TrilaterationFunction;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import static java.lang.Math.pow;

public class DataClass {

    public static double distance (double rssi, int txPower) {
        double distance = 0;
        float i = (float) 0.75;

        if (rssi == 0) {
            return -1.0; // if we cannot determine accuracy, return -1.0
        }
        double ratio = rssi*1.0/txPower;
        if (ratio < 1.0) {
            distance =  pow(ratio,10);
        }
        else {
            distance =  (0.89976) * Math.pow(ratio,7.7095) + 0.111;
        }
        if (distance < 0.1) {
            Log.e("Distance : " ,"low");
        }
        return distance;
    }

    public static LatLng trilateral( ArrayList<HashMap<String,String>> beconsValues, String json){


        float earthR = 6371;


        double[][] positions = new double[beconsValues.size()][3];
        double[] distances = new double[beconsValues.size()];
        for (int i = 0; i < beconsValues.size(); ++i) { //iterate over the elements of the list
            double xA = earthR *(Math.cos(Math.toRadians(Double.parseDouble(beconsValues.get(i).get("latitude")))) * Math.cos(Math.toRadians(Double.parseDouble(beconsValues.get(i).get("longitude")))));
            double yA = earthR *(Math.cos(Math.toRadians(Double.parseDouble(beconsValues.get(i).get("latitude")))) * Math.sin(Math.toRadians(Double.parseDouble(beconsValues.get(i).get("longitude")))));
            double zA = earthR *(Math.sin(Math.toRadians(Double.parseDouble(beconsValues.get(i).get("latitude")))));

            positions[i][0] = xA;
            positions[i][1] = yA;
            positions[i][2] = zA;

            distances[i] = Double.parseDouble(beconsValues.get(i).get("distance"));

        }



        NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
        LeastSquaresOptimizer.Optimum optimum = solver.solve();

        double[] centroid = optimum.getPoint().toArray();
        double value = centroid[2]/earthR;
        double lat = Math.toDegrees(Math.asin(centroid[2] / earthR));
        double lon = Math.toDegrees(Math.atan2(centroid[1],centroid[0])) ;
        Location location =new Location("A");
        location.setLatitude(lat);
        location.setLongitude(lon);
        LatLng latLng = new LatLng(lat,lon);
//        mMap.clear();
//        addMarker(latLng);
        LatLng latLng1 = stickToRoute(location, json);

        Log.e("centroidvalues", centroid.toString());
        return latLng1;
    }

    public static LatLng stickToRoute(Location locationA, String json){

        ArrayList Waypoints = new ArrayList<JSONObject>();
        ArrayList<HashMap<String,String>> sortedlist = new ArrayList<>();
        LatLng newlocation = null;

        try {


            Waypoints = getWaypointsForRoom(json);
            for ( int i = 0; i< Waypoints.size(); i++){
                String[] latlong =  Waypoints.get(i).toString().split(",");
                double latitude = Double.parseDouble(latlong[0]);
                double longitude = Double.parseDouble(latlong[1]);
                LatLng locationB = new LatLng(latitude,longitude);
                Location locB= new Location("B");
                locB.setLatitude(latitude);
                locB.setLongitude(longitude);
                double distance = locationA.distanceTo(locB);
                HashMap<String, String> hashMap = new HashMap<>();
                hashMap.put("location", String.valueOf(Waypoints.get(i)));
                hashMap.put("distance" , String.valueOf(distance));
                sortedlist.add(hashMap);
            }
            Collections.sort(sortedlist, new Comparator<HashMap<String, String>>() {
                @Override
                public int compare(HashMap<String, String> o1, HashMap<String, String> o2) {
                    return o1.get("distance").toString().compareTo(o2.get("distance").toString());
                }
            });
            Log.e("sortedArray", sortedlist.toString());

            String[] strPointA = sortedlist.get(0).get("location").split(",");
            LatLng pointA = new LatLng(Double.parseDouble(strPointA[0]), Double.parseDouble(strPointA[1]));

            String[] strPointB = sortedlist.get(1).get("location").split(",");
            LatLng pointB = new LatLng(Double.parseDouble(strPointB[0]), Double.parseDouble(strPointB[1]));

            LatLng currentPoint = new LatLng(locationA.getLatitude(), locationA.getLongitude());

            //Calculate Slope

            double a = (pointB.longitude - pointA.longitude)/(pointB.latitude - pointA.latitude);
            double c = pointA.longitude - (pointA.latitude * a);
            double b = -1.0;

            //Find closest point

            double x = (b * ((b * currentPoint.latitude) - (a * currentPoint.longitude)) - (a * c))/((a*a) + (b*b));
            double y = (a * ((-1 * b * currentPoint.latitude) + (a * currentPoint.longitude)) - (b * c))/((a*a) + (b*b));

             newlocation = new LatLng(x,y);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return newlocation;
        //12.924542443103322,77.62011999594371
    }

    public static ArrayList getWaypointsForRoom(String json) {
        ArrayList arrayList1 = new ArrayList();
        HashSet hashSet = new HashSet();

        try {

            JSONObject metaData = new JSONObject(json);
            JSONArray buildings = metaData.getJSONArray("Buildings").getJSONObject(0).getJSONArray("Floors");
            try {
                JSONArray getCampusWaypoints = metaData.getJSONArray("CampusWaypoints");

                for (int i = 0; i<getCampusWaypoints.length(); i++){
                  arrayList1.add(getCampusWaypoints.getJSONObject(i).get("PointA").toString().replace("{","").replace("}",""));
                  arrayList1.add(getCampusWaypoints.getJSONObject(i).get("PointB").toString().replace("{","").replace("}",""));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (int j = 0; j<buildings.length(); j++){
                JSONArray jsonArray = buildings.getJSONObject(j).getJSONArray("Waypoints");
                for (int i = 0; i <jsonArray.length(); i++){
                    arrayList1.add(jsonArray.getJSONObject(i).get("PointA").toString().replace("{","").replace("}",""));
                    arrayList1.add(jsonArray.getJSONObject(i).get("PointB").toString().replace("{","").replace("}",""));
                }
            }
            hashSet.addAll(arrayList1);
            arrayList1.clear();
            arrayList1.addAll(hashSet);

        } catch (Exception e) {
            e.printStackTrace();

        }
        return arrayList1;

    }

}
