package upm.softwaredesign.uber.fragments;
import android.Manifest;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.vision.text.Text;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import upm.softwaredesign.uber.MainActivity;
import upm.softwaredesign.uber.R;
import upm.softwaredesign.uber.utilities.Constants;
import upm.softwaredesign.uber.utilities.HttpManager;
import upm.softwaredesign.uber.utilities.ScheduledService;


public class MapViewFragment extends Fragment implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private LatLng latLng;
    private GoogleMap mGoogleMap;
    private MapFragment mFragment;

    private Marker startMarker;
    private Marker destinationMarker;

    private String mDestinationAddress;

    private FloatingActionButton mFloatingActionButton;
    private EditText mDestinationAddressEditText;
    public TextView mTripStatusTextView;

    private enum TripStatus {
        FREE,
        REQUESTED,
        ACTIVE,
        FINISHED
    }

    TripStatus mTripStatus;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map_view, container, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mFragment = (MapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        mFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap gMap) {

        mGoogleMap = gMap;
        mFloatingActionButton   = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        mDestinationAddressEditText = (EditText) getActivity().findViewById(R.id.destination_edit_text);
        mTripStatusTextView = (TextView) getActivity().findViewById(R.id.tripStatusTextView);
        mTripStatus = TripStatus.FREE;
        //mTripStatusTextView.setText("You have not requested any trip");

        if (ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    setUpMyLocation();

        } else {
            ActivityCompat.requestPermissions(getActivity(), new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION }, 200);
        }

        mGoogleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {

            @Override
            public void onMapClick(LatLng point) {
                changeDestinationLocation(point);
            }
        });

        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (mTripStatus == TripStatus.FREE) {

                    if (destinationMarker != null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                        builder.setTitle("Request a cab")
                                .setMessage("Do you want to request a cab from " + startMarker.getPosition().toString() + " to " + destinationMarker.getPosition().toString() + "?")
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        HttpManager httpManager = new HttpManager(getActivity());
                                        httpManager.sendPosition(startMarker.getPosition(), destinationMarker.getPosition());
                                    }
                                })
                                .setNegativeButton(android.R.string.no, null).show();
                    } else {
                        Toast.makeText(getActivity(), "Tap on map to choose destination!", Toast.LENGTH_SHORT).show();
                    }
                } else if (mTripStatus == TripStatus.REQUESTED) {
                    Toast.makeText(getActivity(), "You have already requested a cab, you cannot make new request", Toast.LENGTH_SHORT).show();
                } else if (mTripStatus == TripStatus.ACTIVE) {
                    Toast.makeText(getActivity(), "You are already in a cab to your destination.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), "Your trip status is not FREE.", Toast.LENGTH_SHORT).show();
                }

            }
        });

        mDestinationAddressEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
            // When focus is lost check that the text field has valid values.
                if (!hasFocus) {
                    String address = mDestinationAddressEditText.getText().toString();
                    LatLng location = getLocationFromAddress(getActivity(), address);
                    changeDestinationLocation(location); // TODO - this has to be given more work and checks
                }
            }

        });


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 200: {
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setUpMyLocation();
                }
            }
        }
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            //place marker at current position
            //mGoogleMap.clear();
            latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(latLng);
            markerOptions.title("Current Position");
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            startMarker = mGoogleMap.addMarker(markerOptions);
        }
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000); //5 seconds
        mLocationRequest.setFastestInterval(3000); //3 seconds
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        //mLocationRequest.setSmallestDisplacement(0.1F); //1/10 meter

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    @Override
    public void onLocationChanged(Location location) {
        if (startMarker != null) {
            startMarker.remove();
        }
        latLng = new LatLng(location.getLatitude(), location.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title("Current Position");
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
        startMarker = mGoogleMap.addMarker(markerOptions);
    }

    public String getAddressByLatLong(Double mLat, Double mLong){
        String sPlace = null;
        Geocoder geocoder;
        List<Address> addresses;
        geocoder = new Geocoder(getActivity(), Locale.getDefault());

        try {

            addresses = geocoder.getFromLocation(mLat, mLong, 1);
            String address = addresses.get(0).getAddressLine(0);
            String city = addresses.get(0).getAddressLine(1);
            String country = addresses.get(0).getAddressLine(2);

            String[] splitAddress = address.split(",");
            sPlace = splitAddress[0] + "\n";
            if(city != null && !city.isEmpty()) {
                String[] splitCity = city.split(",");
                sPlace += splitCity[0];
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sPlace;
    }

    public LatLng getLocationFromAddress(Context context, String strAddress) {

        Geocoder coder = new Geocoder(context);
        List<Address> address;
        LatLng p1 = null;

        try {
            address = coder.getFromLocationName(strAddress, 5);
            if (address == null) {
                return null;
            }
            Address location = address.get(0);
            location.getLatitude();
            location.getLongitude();

            p1 = new LatLng(location.getLatitude(), location.getLongitude() );

        } catch (IOException ex) {

            ex.printStackTrace();
        }

        return p1;
    }

    public void changeDestinationLocation(LatLng point) {
        if (mTripStatus == TripStatus.FREE)
        {
            MarkerOptions destinationMarkerOptions = new MarkerOptions().position(new LatLng(point.latitude, point.longitude)).title("Destination");
            if (destinationMarker != null)
                destinationMarker.remove();
            destinationMarker = mGoogleMap.addMarker(destinationMarkerOptions);

            mDestinationAddress = getAddressByLatLong(point.latitude, point.longitude);
            mDestinationAddressEditText.setText(mDestinationAddress);
        } else if (mTripStatus == TripStatus.REQUESTED) {
            Toast.makeText(getActivity(), "You have already requested a cab, you cannot make new request", Toast.LENGTH_SHORT).show();
        } else if (mTripStatus == TripStatus.ACTIVE) {
            Toast.makeText(getActivity(), "You are already in a cab to your destination.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getActivity(), "Your trip status is not FREE.", Toast.LENGTH_SHORT).show();
        }
    }

    public void setUpMyLocation()
    {
        mGoogleMap.setMyLocationEnabled(true);
        mGoogleMap.getUiSettings().setMyLocationButtonEnabled(false);
        buildGoogleApiClient();
        mGoogleApiClient.connect();
        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        Location location = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false));
        if (location != null)
        {
            mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 13));

            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))      // Sets the center of the map to my location
                    .zoom(12)                  // Sets the zoom
                    //.bearing(90)                // Sets the orientation of the camera to east
                    //.tilt(40)                   // Sets the tilt of the camera to 30 degrees
                    .build();                   // Creates a CameraPosition from the builder
            mGoogleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }

    public void startTripStatusCheck()
    {
        // use this to start and trigger a service
        Intent i= new Intent(getActivity(), ScheduledService.class);
        getActivity().startService(i);
    }
}