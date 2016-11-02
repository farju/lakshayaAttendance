/*
 * Copyright (C) 2016 Ferid Cafer
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

package com.ferid.app.classroom.statistics;

import android.content.Context;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ferid.app.classroom.R;
import com.ferid.app.classroom.adapters.StatisticalAdapter;
import com.ferid.app.classroom.database.DatabaseManager;
import com.ferid.app.classroom.interfaces.AdapterClickListener;
import com.ferid.app.classroom.model.Attendance;
import com.ferid.app.classroom.model.AttendanceStatistics;
import com.ferid.app.classroom.model.Classroom;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;

/**
 * Created by ferid.cafer on 4/20/2015.<br />
 * Shows the student attendance rate and graph.
 */
public class StatisticalListActivity extends AppCompatActivity {

    private Context context;

    private RecyclerView list;
    private ArrayList<AttendanceStatistics> attendanceList = new ArrayList<>();
    private StatisticalAdapter adapter;

    private TextView emptyText; //empty list view text

    private Classroom classroom;

    //graphics
    private GraphView graph;
    private LinearLayout graphLayout;
    private AttendanceStatistics attendance;
    private ArrayList<Attendance> graphList = new ArrayList<>();
    //close graph icon
    private ImageButton closeGraphIcon;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.statistics);

        Bundle args = getIntent().getExtras();
        if (args != null) {
            classroom = args.getParcelable("classroom");
        }

        context = this;

        //toolbar
        setToolbar();

        //graph
        graphLayout = (LinearLayout) findViewById(R.id.graphLayout);
        graph = (GraphView) findViewById(R.id.graph);
        closeGraphIcon = (ImageButton) findViewById(R.id.closeGraphIcon);
        TextView className = (TextView) findViewById(R.id.className);
        if (className != null && classroom != null) {
            className.setText(classroom.getName());
        }

        //list
        list = (RecyclerView) findViewById(R.id.list);
        adapter = new StatisticalAdapter(attendanceList);
        list.setAdapter(adapter);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setHasFixedSize(true);

        emptyText = (TextView) findViewById(R.id.emptyText);

        setCloseGraphIconListener();
        addAdapterClickListener();

        new SelectAllAttendancesOfClass().execute();
    }

    /**
     * Create toolbar and set its attributes
     */
    private void setToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        if (classroom != null) {
            setTitle(classroom.getName());
        }
    }

    /**
     * Set empty list text
     */
    private void setEmptyText() {
        if (emptyText != null) {
            if (attendanceList.isEmpty()) {
                emptyText.setVisibility(View.VISIBLE);
            } else {
                emptyText.setVisibility(View.GONE);
            }
        }
    }

    /**
     * List item click event
     */
    private void addAdapterClickListener() {
        adapter.setAdapterClickListener(new AdapterClickListener() {
            @Override
            public void OnItemClick(int position) {
                if (attendanceList != null && attendanceList.size() > position) {
                    attendance = attendanceList.get(position);
                    graphList.clear();

                    new SelectAllAttendancesOfStudent().execute();
                }
            }
        });
    }

    /**
     * setOnClickListener
     */
    private void setCloseGraphIconListener() {
        closeGraphIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideGraph();
            }
        });
    }

    /**
     * Select all students' attendances in percentage from DB
     */
    private class SelectAllAttendancesOfClass extends AsyncTask<Void, Void, ArrayList<AttendanceStatistics>> {

        @Override
        protected ArrayList<AttendanceStatistics> doInBackground(Void... params) {
            ArrayList<AttendanceStatistics> tmpList = null;
            if (classroom != null) {
                DatabaseManager databaseManager = new DatabaseManager(context);
                tmpList = databaseManager.selectAllAttendancesOfClass(classroom.getId());
            }

            return tmpList;
        }

        @Override
        protected void onPostExecute(ArrayList<AttendanceStatistics> tmpList) {
            attendanceList.clear();

            if (tmpList != null) {
                attendanceList.addAll(tmpList);
                adapter.notifyDataSetChanged();

                setEmptyText();
            }
        }
    }

    /**
     * Select a student's all attendances from DB
     */
    private class SelectAllAttendancesOfStudent extends AsyncTask<Void, Void, ArrayList<Attendance>> {

        @Override
        protected ArrayList<Attendance> doInBackground(Void... params) {
            ArrayList<Attendance> tmpList = null;
            if (attendance != null) {
                DatabaseManager databaseManager = new DatabaseManager(context);
                tmpList = databaseManager.selectAllAttendancesOfStudent(attendance.getClassroomId(),
                        attendance.getStudentId());
            }

            return tmpList;
        }

        @Override
        protected void onPostExecute(ArrayList<Attendance> tmpList) {
            graphList.clear();

            if (tmpList != null) {
                graphList.addAll(tmpList);

                calculateAttendanceByWeek();
            }
        }
    }

    /**
     * Calculate presence percentage by week
     */
    private void calculateAttendanceByWeek() {
        ArrayList<Integer> presenceList = new ArrayList<>();
        int numberOfWeeks = graphList.size();
        int numberOfPresence = 0;

        for (int i = 0; i < numberOfWeeks; i++) {
            Attendance tmpAttendance = graphList.get(i);

            if (tmpAttendance.getPresent() == 1) {
                numberOfPresence++;
            }

            int percentage = (int) ((double)numberOfPresence * 100 / (i+1));
            presenceList.add(percentage);
        }

        prepareGraphics(presenceList);
    }

    /**
     * Set graph style attributes
     * @param maxX
     */
    private void setGraphAttributes(int maxX) {
        graph.setTitle(attendance.getStudentName());
        graph.setTitleColor(ContextCompat.getColor(this, R.color.primary_text));

        graph.getViewport().setMaxY(100);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMaxX(maxX);
        graph.getViewport().setXAxisBoundsManual(true);

        graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.BOTH);
        graph.getGridLabelRenderer().setGridColor(ContextCompat.getColor(this, R.color.grey));
        graph.getGridLabelRenderer().setHorizontalLabelsColor(ContextCompat.getColor(this,
                R.color.blackish));
        graph.getGridLabelRenderer().setVerticalLabelsColor(ContextCompat.getColor(this,
                R.color.blackish));

        //number of x-axis label items
        int numHorizontalLabels;
        if (maxX <= 8) {
            numHorizontalLabels = maxX + 1;
        } else if (maxX <= 16) {
            numHorizontalLabels = maxX / 2 + 1;
        } else {
            numHorizontalLabels = maxX / 4 + 1;
        }
        graph.getGridLabelRenderer().setNumHorizontalLabels(numHorizontalLabels);

        graph.getGridLabelRenderer().reloadStyles();
    }

    /**
     * Draw graph of weekly attendance
     * @param presenceList
     */
    private void prepareGraphics(ArrayList<Integer> presenceList) {
        DataPoint[] dataPoints = new DataPoint[presenceList.size() + 1];
        dataPoints[0] = new DataPoint(0, 0);
        for (int i = 0; i < presenceList.size(); i++) {
            dataPoints[i + 1] = new DataPoint((i+1), presenceList.get(i));
        }

        LineGraphSeries<DataPoint> series = new LineGraphSeries<>(dataPoints);
        series.setColor(ContextCompat.getColor(this, R.color.colourAccent));
        series.setThickness(getResources().getInteger(R.integer.statistics_series_thickness));
        series.setAnimated(true);

        graph.removeAllSeries();
        graph.addSeries(series);

        setGraphAttributes(presenceList.size());

        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                int valueInt = ((int) value);

                if (isValueX && valueInt == 0) {
                    return "";
                } else {
                    return super.formatLabel(valueInt, isValueX);
                }
            }
        });

        showGraph();
    }

    /**
     * Show graph layout with its full content
     */
    private void showGraph() {
        if (graphLayout.getVisibility() != View.VISIBLE) {
            Animation animShow = AnimationUtils.loadAnimation(context, R.anim.push_from_bottom);
            graphLayout.startAnimation(animShow);
            graphLayout.setVisibility(View.VISIBLE);

            setStatusBarColour();
        }
    }

    /**
     * Hide graph layout
     */
    private void hideGraph() {
        if (graphLayout.getVisibility() == View.VISIBLE) {
            Animation animHide = AnimationUtils.loadAnimation(context,
                    R.anim.push_to_bottom);
            graphLayout.setAnimation(animHide);
            graphLayout.setVisibility(View.GONE);

            setStatusBarColour();
        }
    }

    /**
     * Set status bar colour on graph visibility change
     */
    private void setStatusBarColour() {
        if (Build.VERSION.SDK_INT >= 21) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

            if (graphLayout.getVisibility() == View.VISIBLE) {
                Configuration configuration = getResources().getConfiguration();

                if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    window.setStatusBarColor(ContextCompat.getColor(this,
                            R.color.materialLightGreen));
                } else if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    window.setStatusBarColor(ContextCompat.getColor(this,
                            R.color.colourPrimaryDark));
                }
            } else {
                window.setStatusBarColor(ContextCompat.getColor(this, R.color.colourPrimaryDark));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        boolean isGraphVisible;
        if (graphLayout.getVisibility() == View.VISIBLE) {
            isGraphVisible = true;
        } else {
            isGraphVisible = false;
        }

        outState.putBoolean("isGraphVisible", isGraphVisible);
        outState.putParcelable("attendance", attendance);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        boolean isGraphVisible = savedInstanceState.getBoolean("isGraphVisible");
        attendance = savedInstanceState.getParcelable("attendance");

        if (isGraphVisible) {
            graphList.clear();

            new SelectAllAttendancesOfStudent().execute();
        }
    }

    private void closeWindow() {
        finish();
        overridePendingTransition(R.anim.stand_still, R.anim.move_out_to_bottom);
    }

    @Override
    public void onBackPressed() {
        //if the graph is open, close it
        if (graphLayout.getVisibility() == View.VISIBLE) {
            hideGraph();
        } else { //otherwise leave the screen
            closeWindow();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar actions click
        switch (item.getItemId()) {
            case android.R.id.home:
                closeWindow();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}