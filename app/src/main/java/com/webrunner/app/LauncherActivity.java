package com.webrunner.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class LauncherActivity extends AppCompatActivity {

    private static final int PICK_FOLDER = 1001;
    private final List<AppInfo> apps = new ArrayList<>();
    private RecyclerView recycler;
    private TextView empty;
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        recycler = findViewById(R.id.recycler);
        empty = findViewById(R.id.empty);
        Button btnCustom = findViewById(R.id.btn_custom);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppAdapter();
        recycler.setAdapter(adapter);

        btnCustom.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(i, PICK_FOLDER);
        });

        scan();
    }

    private void scan() {
        apps.clear();
        File root = new File(Environment.getExternalStorageDirectory(), ".webrunner");
        if (!root.exists()) root.mkdirs();

        File[] folders = root.listFiles(File::isDirectory);
        if (folders != null) {
            for (File f : folders) {
                File index = new File(f, "index.html");
                if (index.exists()) {
                    AppInfo info = new AppInfo();
                    info.name = f.getName();
                    info.path = index.getAbsolutePath();
                    info.desc = "Tap to open";

                    File infoFile = new File(f, "info.txt");
                    if (infoFile.exists()) {
                        try (BufferedReader br = new BufferedReader(new FileReader(infoFile))) {
                            String line = br.readLine();
                            if (line != null) info.desc = line.trim();
                        } catch (Exception ignored) {}
                    }
                    apps.add(info);
                }
            }
        }

        adapter.notifyDataSetChanged();
        empty.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(apps.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                DocumentFile dir = DocumentFile.fromTreeUri(this, uri);
                if (dir != null) {
                    DocumentFile index = dir.findFile("index.html");
                    if (index != null) {
                        Intent i = new Intent(this, MainActivity.class);
                        i.putExtra("saf_uri", index.getUri().toString());
                        startActivity(i);
                    } else {
                        Toast.makeText(this, "No index.html found", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    static class AppInfo {
        String name, desc, path;
    }

    class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AppInfo a = apps.get(pos);
            h.name.setText(a.name);
            h.desc.setText(a.desc);
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(LauncherActivity.this, MainActivity.class);
                i.putExtra("file_path", a.path);
                startActivity(i);
            });
        }

        @Override
        public int getItemCount() { return apps.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, desc;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.name);
                desc = v.findViewById(R.id.desc);
            }
        }
    }
}
