package cn.sixnine.filmlog;

import android.content.Intent;
import android.os.Bundle;

/** Retains the legacy component entry without a second search/filter interface. */
public final class ManagementActivity extends WorkspaceActivity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);startActivity(new Intent(this,MainActivity.class).putExtra("show_roll_search",true).putExtra("query",getIntent().getStringExtra("query")));finish();}
}
