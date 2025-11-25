package com.audion.app.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.io.Serializable;

@Entity(tableName = "hearing_profiles")
public class HearingProfile implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String name;
    private String icon;

    public HearingProfile(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    // Getters and setters
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getIcon() {
        return icon;
    }
    public void setIcon(String icon) {
        this.icon = icon;
    }
}
