package com.webrunner.app;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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
    private File appsRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        // Private folder - no special permission needed
        appsRoot = new File(getExternalFilesDir(null), ".webrunner");
        if (!appsRoot.exists()) {
            appsRoot.mkdirs();
        }

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

        File[] folders = appsRoot.listFiles(File::isDirectory);
        if (folders != null) {
            for (File f : folders) {
                File index = new File(f, "index.html");
                if (index.exists()) {
                    AppInfo info = new AppInfo();
                    info.name = f.getName();
                    info.path = index.getAbsolutePath();
                    info.desc = "Tap to open";

                    // Optional description
                    File infoFile = new File(f, "info.txt");
                    if (infoFile.exists()) {
                        try (BufferedReader br = new BufferedReader(new FileReader(infoFile))) {
                            String line = br.readLine();
                            if (line != null) info.desc = line.trim();
                        } catch (Exception ignored) {}
                    }

                    // Optional icon
                    File icon = new File(f, "icon.png");
                    if (!icon.exists()) icon = new File(f, "icon.jpg");
                    if (icon.exists()) {
                        info.iconPath = icon.getAbsolutePath();
                    }

                    apps.add(info);
                }
            }
        }

        adapter.notifyDataSetChanged();

        if (apps.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            empty.setText("No apps found.\n\nPut your apps here:\n\n" +
                    appsRoot.getAbsolutePath() +
                    "\n\nCreate a folder for each app and put index.html inside it.");
            recycler.setVisibility(View.GONE);
        } else {
            empty.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }
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
        String name;
        String desc;
        String path;
        String iconPath;
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

            if (a.iconPath != null) {
                h.icon.setImageBitmap(BitmapFactory.decodeFile(a.iconPath));
            } else {
                h.icon.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(LauncherActivity.this, MainActivity.class);
                i.putExtra("file_path", a.path);
                startActivity(i);
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name, desc;

            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.icon);
                name = v.findViewById(R.id.name);
                desc = v.findViewById(R.id.desc);
            }
        }
    }
}
