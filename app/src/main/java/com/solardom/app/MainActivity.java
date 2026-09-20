package com.solardom.app;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.Location;
import android.location.LocationManager;
import android.content.Context;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {
    EditText consumo, panelW, hsp, perdidas, bateria, dod, autonomia, tarifa;
    TextView resultado, brujula, orientacion, ubicacion;
    SensorManager sm; Sensor rot; float azimuth=0;
    final int REQ=77;

    int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    TextView tv(String s,int size){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(Color.DKGRAY); t.setPadding(dp(4),dp(6),dp(4),dp(6)); return t;
    }
    EditText input(String hint,String value){
        EditText e=new EditText(this); e.setHint(hint); e.setText(value);
        e.setInputType(2|8192); e.setPadding(dp(10),dp(8),dp(10),dp(8)); return e;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        sm=(SensorManager)getSystemService(SENSOR_SERVICE);
        rot=sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(14),dp(16),dp(16));

        ScrollView scroll=new ScrollView(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(box);

        TextView title=tv("☀  SOLARDOM",28);
        title.setTextColor(Color.rgb(21,101,192)); title.setGravity(Gravity.CENTER);
        box.addView(title);
        TextView sub=tv("Calculadora solar + brújula de orientación",16);
        sub.setGravity(Gravity.CENTER); box.addView(sub);

        box.addView(tv("CONSUMO Y SISTEMA",18));
        consumo=input("Consumo diario (kWh/día)","20"); box.addView(consumo);
        panelW=input("Potencia del panel (W)","550"); box.addView(panelW);
        hsp=input("Horas sol pico HSP","5"); box.addView(hsp);
        perdidas=input("Pérdidas del sistema (%)","20"); box.addView(perdidas);
        bateria=input("Capacidad batería (kWh)","10"); box.addView(bateria);
        dod=input("Profundidad de descarga DoD (%)","80"); box.addView(dod);
        autonomia=input("Autonomía deseada (días)","1"); box.addView(autonomia);
        tarifa=input("Tarifa eléctrica (RD$/kWh, opcional)","12"); box.addView(tarifa);

        Button calc=new Button(this); calc.setText("CALCULAR SISTEMA"); box.addView(calc);
        resultado=tv("Introduce los datos y pulsa CALCULAR.",16); box.addView(resultado);

        box.addView(tv("ORIENTACIÓN ÓPTIMA",18));
        ubicacion=tv("Ubicación: buscando GPS…",15); box.addView(ubicacion);
        orientacion=tv("Objetivo: Sur verdadero • inclinación según latitud",16); box.addView(orientacion);
        brujula=tv("🧭 0° • N",30); brujula.setGravity(Gravity.CENTER); box.addView(brujula);
        box.addView(tv("Alinea el teléfono con el panel. La dirección se expresa respecto al norte magnético; la app calcula el objetivo para el sur verdadero cuando tiene GPS.",13));

        root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        setContentView(root);

        calc.setOnClickListener(v->calcular());
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ);
        else getLocation();
    }

    double val(EditText e){
        try{return Double.parseDouble(e.getText().toString().replace(',','.'));}catch(Exception x){return 0;}
    }

    void calcular(){
        double c=val(consumo), pw=val(panelW)/1000.0, sun=val(hsp), loss=val(perdidas)/100.0;
        if(c<=0||pw<=0||sun<=0){resultado.setText("Revisa consumo, potencia y HSP.");return;}
        double prod=pw*sun*(1-loss);
        int n=(int)Math.ceil(c/prod);
        double kwp=n*pw, day=n*prod, month=day*30;

        double bat=val(bateria), d=val(dod)/100.0, aut=val(autonomia);
        double usable=bat*d, needBat=c*aut;
        double battCoverage=c>0?Math.min(100,usable/c*100):0;
        double monthlyEnergy=Math.min(c,usable)*30;
        double savingsPct=c>0?Math.min(100,monthlyEnergy/(c*30)*100):0;
        double cost=val(tarifa), annualAvoided=monthlyEnergy*12*cost;

        resultado.setText(String.format(Locale.US,
            "Paneles necesarios: %d\nPotencia FV: %.2f kWp\nProducción estimada: %.1f kWh/día • %.0f kWh/mes\n\n"+
            "Batería instalada: %.1f kWh\nEnergía útil (DoD): %.1f kWh\nCapacidad para %.1f día(s): %.1f kWh\n"+
            "Cobertura teórica de la carga con esa batería: %.1f%%\nEnergía desplazada estimada/mes: %.0f kWh\n"+
            "Ahorro energético equivalente: %.1f%%\nAhorro económico indicativo/año: RD$ %.0f",
            n,kwp,day,month,bat,usable,aut,needBat,battCoverage,monthlyEnergy,savingsPct,annualAvoided));
    }

    void getLocation(){
        try{
            LocationManager lm=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
            if(lm!=null){
                Location a=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if(a==null)a=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if(a!=null) actualizarOrientacion(a);
                else ubicacion.setText("Ubicación: no disponible todavía. Activa GPS.");
            }
        }catch(Exception e){ubicacion.setText("Ubicación GPS no disponible.");}
    }

    void actualizarOrientacion(Location l){
        double lat=l.getLatitude(), lon=l.getLongitude();
        float decl=0;
        try{
            GeomagneticField gf=new GeomagneticField((float)lat,(float)lon,(float)l.getAltitude(),System.currentTimeMillis());
            decl=gf.getDeclination();
        }catch(Exception ignored){}
        double magneticTarget=(180.0-decl+360)%360;
        double tilt=Math.max(5,Math.min(35,Math.abs(lat)));
        ubicacion.setText(String.format(Locale.US,"Ubicación: %.5f°, %.5f°",lat,lon));
        orientacion.setText(String.format(Locale.US,
            "Sur verdadero: 180°  |  objetivo magnético aprox.: %.1f°\nInclinación anual aproximada: %.1f°",
            magneticTarget,tilt));
    }

    String dir(float a){
        String[] d={"N","NE","E","SE","S","SO","O","NO"};
        return d[(int)Math.round(a/45)%8];
    }

    @Override public void onSensorChanged(SensorEvent e){
        if(e.sensor.getType()!=Sensor.TYPE_ROTATION_VECTOR)return;
        float[] R=new float[9];
        SensorManager.getRotationMatrixFromVector(R,e.values);
        float[] o=new float[3];
        SensorManager.getOrientation(R,o);
        azimuth=(float)Math.toDegrees(o[0]);
        if(azimuth<0)azimuth+=360;
        brujula.setText(String.format(Locale.US,"🧭 %.0f° • %s",azimuth,dir(azimuth)));
    }
    @Override public void onAccuracyChanged(Sensor s,int a){}
    @Override protected void onResume(){super.onResume();if(rot!=null)sm.registerListener(this,rot,SensorManager.SENSOR_DELAY_UI);}
    @Override protected void onPause(){super.onPause();sm.unregisterListener(this);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==REQ&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)getLocation();
    }
}
