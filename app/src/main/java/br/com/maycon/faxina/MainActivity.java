package br.com.maycon.faxina;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Faxina: escaneia o armazenamento do celular, mostra os arquivos e pastas
 * mais pesados e permite apagar os selecionados.
 */
public class MainActivity extends Activity {

    // Arquivos menores que isso não entram na lista (mas contam nos totais)
    private static final long TAMANHO_MINIMO = 1024L * 1024L;          // 1 MB
    private static final long PASTA_MINIMA = 10L * 1024L * 1024L;      // 10 MB
    private static final int MAX_ARQUIVOS = 3000;
    private static final int MAX_PASTAS = 300;

    private static final String[] FILTROS = {
            "Todos", "Vídeos", "Fotos", "Áudios", "Documentos", "APKs", "Outros",
            "WhatsApp", "Downloads"
    };

    static class Item {
        File file;
        long size;
        long modified;
        String cat;
        boolean checked;
        boolean isDir;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Item> arquivos = new ArrayList<>();
    private final List<Item> pastas = new ArrayList<>();
    private final List<Item> visiveis = new ArrayList<>();
    private final Map<String, Long> totaisCategoria = new LinkedHashMap<>();
    private Map<String, Long> tamanhoPastas = new HashMap<>();
    private String rootPath;

    private String filtro = "Todos";
    private String filtroPasta = null;
    private boolean modoPastas = false;
    private volatile boolean escaneando = false;
    private boolean escanearAoVoltar = false;

    private TextView resumoArmazenamento, resumoCategorias, status;
    private ProgressBar barra;
    private Button btnEscanear, btnModo, btnMarcar, btnApagar, btnLimparPasta;
    private Spinner spinner;
    private ListView lista;
    private Adaptador adaptador;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String fmt(long bytes) {
        return Formatter.formatFileSize(this, bytes);
    }

    // ---------------------------------------------------------------- tela

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(dp(14), dp(12), dp(14), dp(10));

        resumoArmazenamento = new TextView(this);
        resumoArmazenamento.setTextSize(16);
        resumoArmazenamento.setTypeface(Typeface.DEFAULT_BOLD);
        raiz.addView(resumoArmazenamento);

        barra = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barra.setMax(1000);
        raiz.addView(barra, new LinearLayout.LayoutParams(-1, dp(14)));

        resumoCategorias = new TextView(this);
        resumoCategorias.setTextSize(13);
        resumoCategorias.setPadding(0, dp(4), 0, dp(6));
        resumoCategorias.setText("Toque em ESCANEAR para descobrir o que está ocupando espaço.");
        raiz.addView(resumoCategorias);

        LinearLayout linha1 = new LinearLayout(this);
        btnEscanear = new Button(this);
        btnEscanear.setText("Escanear");
        btnEscanear.setOnClickListener(v -> escanear());
        linha1.addView(btnEscanear, new LinearLayout.LayoutParams(0, -2, 1));
        Button btnSistema = new Button(this);
        btnSistema.setText("Armaz. do sistema");
        btnSistema.setOnClickListener(v -> abrirArmazenamentoSistema());
        linha1.addView(btnSistema, new LinearLayout.LayoutParams(0, -2, 1));
        raiz.addView(linha1);

        LinearLayout linha2 = new LinearLayout(this);
        linha2.setGravity(Gravity.CENTER_VERTICAL);
        spinner = new Spinner(this);
        ArrayAdapter<String> sa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, FILTROS);
        spinner.setAdapter(sa);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                filtro = FILTROS[pos];
                atualizarLista();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        linha2.addView(spinner, new LinearLayout.LayoutParams(0, -2, 1));
        btnModo = new Button(this);
        btnModo.setText("Ver pastas");
        btnModo.setOnClickListener(v -> {
            modoPastas = !modoPastas;
            atualizarLista();
        });
        linha2.addView(btnModo, new LinearLayout.LayoutParams(0, -2, 1));
        raiz.addView(linha2);

        LinearLayout linha3 = new LinearLayout(this);
        linha3.setGravity(Gravity.CENTER_VERTICAL);
        status = new TextView(this);
        status.setTextSize(13);
        linha3.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        btnLimparPasta = new Button(this);
        btnLimparPasta.setText("✕ pasta");
        btnLimparPasta.setVisibility(View.GONE);
        btnLimparPasta.setOnClickListener(v -> {
            filtroPasta = null;
            atualizarLista();
        });
        linha3.addView(btnLimparPasta);
        raiz.addView(linha3);

        lista = new ListView(this);
        adaptador = new Adaptador();
        lista.setAdapter(adaptador);
        lista.setOnItemClickListener((p, v, pos, id) -> tocarItem(visiveis.get(pos)));
        lista.setOnItemLongClickListener((p, v, pos, id) -> {
            Item it = visiveis.get(pos);
            if (!it.isDir) abrirArquivo(it.file);
            return true;
        });
        raiz.addView(lista, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout rodape = new LinearLayout(this);
        btnMarcar = new Button(this);
        btnMarcar.setText("Marcar todos");
        btnMarcar.setOnClickListener(v -> marcarTodos());
        rodape.addView(btnMarcar, new LinearLayout.LayoutParams(0, -2, 1));
        btnApagar = new Button(this);
        btnApagar.setTextColor(Color.rgb(198, 40, 40));
        btnApagar.setOnClickListener(v -> confirmarApagar());
        rodape.addView(btnApagar, new LinearLayout.LayoutParams(0, -2, 1));
        raiz.addView(rodape);

        setContentView(raiz);
        atualizarResumoArmazenamento();
        atualizarLista();
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarResumoArmazenamento();
        if (escanearAoVoltar && temPermissao()) {
            escanearAoVoltar = false;
            escanear();
        }
    }

    private void atualizarResumoArmazenamento() {
        try {
            StatFs st = new StatFs(Environment.getDataDirectory().getPath());
            long total = st.getTotalBytes();
            long livre = st.getAvailableBytes();
            long usado = total - livre;
            resumoArmazenamento.setText("Usado " + fmt(usado) + " de " + fmt(total)
                    + "  •  Livre " + fmt(livre));
            barra.setProgress(total > 0 ? (int) (usado * 1000 / total) : 0);
        } catch (Exception e) {
            resumoArmazenamento.setText("Armazenamento");
        }
    }

    // ---------------------------------------------------------- permissão

    private boolean temPermissao() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void pedirPermissao() {
        new AlertDialog.Builder(this)
                .setTitle("Permissão necessária")
                .setMessage("Para encontrar e apagar arquivos pesados, o Faxina precisa de acesso "
                        + "a todos os arquivos.\n\nNa próxima tela, ative \"Permitir acesso para "
                        + "gerenciar todos os arquivos\" e volte para o app.")
                .setPositiveButton("Continuar", (d, w) -> {
                    escanearAoVoltar = true;
                    if (Build.VERSION.SDK_INT >= 30) {
                        try {
                            startActivity(new Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception e) {
                            startActivity(new Intent(
                                    Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    } else {
                        requestPermissions(new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        super.onRequestPermissionsResult(rc, perms, res);
        if (temPermissao()) {
            escanearAoVoltar = false;
            escanear();
        }
    }

    // ----------------------------------------------------------- varredura

    private static String extensao(String nome) {
        int i = nome.lastIndexOf('.');
        return i < 0 ? "" : nome.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    static String categoria(File f) {
        switch (extensao(f.getName())) {
            case "mp4": case "mkv": case "mov": case "3gp": case "avi": case "webm":
            case "m4v": case "ts": case "wmv": case "flv":
                return "Vídeos";
            case "jpg": case "jpeg": case "png": case "gif": case "heic": case "heif":
            case "webp": case "dng": case "raw": case "bmp":
                return "Fotos";
            case "mp3": case "m4a": case "aac": case "opus": case "ogg": case "wav":
            case "flac": case "amr": case "wma":
                return "Áudios";
            case "apk": case "xapk": case "apks": case "apkm":
                return "APKs";
            case "pdf": case "doc": case "docx": case "xls": case "xlsx": case "ppt":
            case "pptx": case "zip": case "rar": case "7z": case "txt": case "csv":
            case "epub": case "tar": case "gz":
                return "Documentos";
            default:
                return "Outros";
        }
    }

    private boolean ignorar(File dir) {
        // Dados internos de outros apps: apagar pode quebrar o app (e no Android 11+
        // o sistema nem deixa ler). Pastas de lixeira ocultas também ficam de fora.
        String p = dir.getAbsolutePath();
        return p.equals(rootPath + "/Android/data") || p.equals(rootPath + "/Android/obb");
    }

    private void escanear() {
        if (!temPermissao()) {
            pedirPermissao();
            return;
        }
        if (escaneando) return;
        escaneando = true;
        btnEscanear.setEnabled(false);
        btnEscanear.setText("Escaneando…");
        status.setText("Começando…");

        new Thread(() -> {
            File root = new File(rootPath);
            List<Item> achados = new ArrayList<>();
            Map<String, Long> dirs = new HashMap<>();
            Map<String, Long> cats = new LinkedHashMap<>();
            for (String c : new String[]{"Vídeos", "Fotos", "Áudios", "Documentos", "APKs", "Outros"})
                cats.put(c, 0L);
            long qtd = 0, total = 0, ultimo = 0;

            Deque<File> pilha = new ArrayDeque<>();
            pilha.push(root);
            while (!pilha.isEmpty()) {
                File dir = pilha.pop();
                File[] filhos = dir.listFiles();
                if (filhos == null) continue;
                for (File f : filhos) {
                    if (f.isDirectory()) {
                        if (!ignorar(f) && !ehLink(f)) pilha.push(f);
                        continue;
                    }
                    long s = f.length();
                    qtd++;
                    total += s;
                    String cat = categoria(f);
                    cats.put(cat, cats.get(cat) + s);
                    somarPastas(dirs, f.getParent(), s);
                    if (s >= TAMANHO_MINIMO) {
                        Item it = new Item();
                        it.file = f;
                        it.size = s;
                        it.modified = f.lastModified();
                        it.cat = cat;
                        achados.add(it);
                    }
                }
                long agora = System.currentTimeMillis();
                if (agora - ultimo > 250) {
                    ultimo = agora;
                    final String msg = "Escaneando… " + qtd + " arquivos (" + fmt(total) + ")";
                    ui.post(() -> status.setText(msg));
                }
            }

            achados.sort((a, b) -> Long.compare(b.size, a.size));
            if (achados.size() > MAX_ARQUIVOS)
                achados = new ArrayList<>(achados.subList(0, MAX_ARQUIVOS));

            final List<Item> fArq = achados;
            final long fQtd = qtd, fTotal = total;
            ui.post(() -> {
                arquivos.clear();
                arquivos.addAll(fArq);
                tamanhoPastas = dirs;
                reconstruirPastas();
                totaisCategoria.clear();
                totaisCategoria.putAll(cats);
                escaneando = false;
                btnEscanear.setEnabled(true);
                btnEscanear.setText("Escanear de novo");
                atualizarResumoCategorias(fQtd, fTotal);
                atualizarResumoArmazenamento();
                atualizarLista();
            });
        }).start();
    }

    private static boolean ehLink(File f) {
        try {
            if (Build.VERSION.SDK_INT >= 26)
                return java.nio.file.Files.isSymbolicLink(f.toPath());
            return !f.getCanonicalPath().equals(f.getAbsolutePath());
        } catch (Exception e) {
            return true;
        }
    }

    private void somarPastas(Map<String, Long> dirs, String pasta, long s) {
        if (pasta == null) return;
        String p = pasta;
        while (p.length() > rootPath.length() && p.startsWith(rootPath)) {
            dirs.merge(p, s, Long::sum);
            int i = p.lastIndexOf('/');
            if (i <= 0) break;
            p = p.substring(0, i);
        }
    }

    private void reconstruirPastas() {
        pastas.clear();
        for (Map.Entry<String, Long> e : tamanhoPastas.entrySet()) {
            if (e.getValue() < PASTA_MINIMA) continue;
            Item it = new Item();
            it.file = new File(e.getKey());
            it.size = e.getValue();
            it.isDir = true;
            it.cat = "Pasta";
            pastas.add(it);
        }
        pastas.sort((a, b) -> Long.compare(b.size, a.size));
        if (pastas.size() > MAX_PASTAS) pastas.subList(MAX_PASTAS, pastas.size()).clear();
    }

    private void atualizarResumoCategorias(long qtd, long total) {
        StringBuilder sb = new StringBuilder();
        sb.append(qtd).append(" arquivos, ").append(fmt(total)).append(" nos seus arquivos\n");
        List<Map.Entry<String, Long>> ord = new ArrayList<>(totaisCategoria.entrySet());
        ord.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        boolean primeiro = true;
        for (Map.Entry<String, Long> e : ord) {
            if (e.getValue() == 0) continue;
            if (!primeiro) sb.append("  •  ");
            sb.append(e.getKey()).append(" ").append(fmt(e.getValue()));
            primeiro = false;
        }
        Long wa = tamanhoPastas.get(rootPath + "/Android/media/com.whatsapp");
        Long waAntigo = tamanhoPastas.get(rootPath + "/WhatsApp");
        long waTotal = (wa == null ? 0 : wa) + (waAntigo == null ? 0 : waAntigo);
        if (waTotal > 0) sb.append("\nWhatsApp: ").append(fmt(waTotal));
        sb.append("\n(Apps e sistema não aparecem aqui — veja em \"Armaz. do sistema\")");
        resumoCategorias.setText(sb.toString());
    }

    // --------------------------------------------------------------- lista

    private boolean passaFiltro(Item it) {
        String p = it.file.getAbsolutePath();
        if (filtroPasta != null && !p.startsWith(filtroPasta + "/") && !p.equals(filtroPasta))
            return false;
        switch (filtro) {
            case "Todos":
                return true;
            case "WhatsApp":
                return p.contains("/com.whatsapp/") || p.startsWith(rootPath + "/WhatsApp/")
                        || p.equals(rootPath + "/WhatsApp") || p.endsWith("/com.whatsapp");
            case "Downloads":
                return p.startsWith(rootPath + "/Download");
            default:
                return !it.isDir && filtro.equals(it.cat);
        }
    }

    private void atualizarLista() {
        visiveis.clear();
        List<Item> fonte = modoPastas ? pastas : arquivos;
        long soma = 0;
        for (Item it : fonte) {
            if (passaFiltro(it)) {
                visiveis.add(it);
                if (!modoPastas) soma += it.size;
            }
        }
        btnModo.setText(modoPastas ? "Ver arquivos" : "Ver pastas");
        btnMarcar.setEnabled(!modoPastas);
        btnLimparPasta.setVisibility(filtroPasta == null ? View.GONE : View.VISIBLE);

        if (!escaneando) {
            String txt;
            if (arquivos.isEmpty() && pastas.isEmpty()) txt = "";
            else if (modoPastas) txt = visiveis.size() + " pastas — toque numa pasta para ver os arquivos dela";
            else txt = visiveis.size() + " arquivos acima de 1 MB (" + fmt(soma) + ")";
            if (filtroPasta != null) txt = "Pasta: " + relativo(filtroPasta) + "\n" + txt;
            status.setText(txt);
        }
        adaptador.notifyDataSetChanged();
        atualizarBotaoApagar();
    }

    private String relativo(String p) {
        if (p.startsWith(rootPath)) {
            String r = p.substring(rootPath.length());
            return r.isEmpty() ? "/" : r;
        }
        return p;
    }

    private void tocarItem(Item it) {
        if (it.isDir) {
            filtroPasta = it.file.getAbsolutePath();
            filtro = "Todos";
            spinner.setSelection(0);
            modoPastas = false;
            atualizarLista();
        } else {
            it.checked = !it.checked;
            adaptador.notifyDataSetChanged();
            atualizarBotaoApagar();
        }
    }

    private void marcarTodos() {
        boolean algumDesmarcado = false;
        for (Item it : visiveis) if (!it.checked) { algumDesmarcado = true; break; }
        for (Item it : visiveis) it.checked = algumDesmarcado;
        adaptador.notifyDataSetChanged();
        atualizarBotaoApagar();
    }

    private List<Item> selecionados() {
        List<Item> r = new ArrayList<>();
        for (Item it : arquivos) if (it.checked) r.add(it);
        return r;
    }

    private void atualizarBotaoApagar() {
        List<Item> sel = selecionados();
        long s = 0;
        for (Item it : sel) s += it.size;
        btnApagar.setEnabled(!sel.isEmpty());
        btnApagar.setText(sel.isEmpty() ? "Apagar" : "Apagar " + sel.size() + " (" + fmt(s) + ")");
        boolean algumDesmarcado = false;
        for (Item it : visiveis) if (!it.checked) { algumDesmarcado = true; break; }
        btnMarcar.setText(algumDesmarcado || visiveis.isEmpty() ? "Marcar todos" : "Desmarcar todos");
    }

    // -------------------------------------------------------------- apagar

    private void confirmarApagar() {
        List<Item> sel = selecionados();
        if (sel.isEmpty()) return;
        long s = 0;
        for (Item it : sel) s += it.size;
        StringBuilder exemplos = new StringBuilder();
        for (int i = 0; i < Math.min(5, sel.size()); i++)
            exemplos.append("• ").append(sel.get(i).file.getName()).append("\n");
        if (sel.size() > 5) exemplos.append("… e mais ").append(sel.size() - 5).append("\n");

        new AlertDialog.Builder(this)
                .setTitle("Apagar " + sel.size() + " arquivo(s)?")
                .setMessage("Vai liberar " + fmt(s) + ".\n\n" + exemplos
                        + "\nOs arquivos são apagados de vez (não vão para a lixeira).")
                .setPositiveButton("Apagar", (d, w) -> apagar(sel))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void apagar(List<Item> sel) {
        btnApagar.setEnabled(false);
        status.setText("Apagando…");
        new Thread(() -> {
            List<Item> ok = new ArrayList<>();
            int falhas = 0;
            long liberado = 0;
            for (Item it : sel) {
                if (it.file.delete() || !it.file.exists()) {
                    ok.add(it);
                    liberado += it.size;
                } else {
                    falhas++;
                }
            }
            String[] caminhos = new String[ok.size()];
            for (int i = 0; i < ok.size(); i++) caminhos[i] = ok.get(i).file.getAbsolutePath();
            if (caminhos.length > 0)
                MediaScannerConnection.scanFile(getApplicationContext(), caminhos, null, null);

            final long fLib = liberado;
            final int fFal = falhas;
            ui.post(() -> {
                arquivos.removeAll(ok);
                for (Item it : ok) somarPastas(tamanhoPastas, it.file.getParent(), -it.size);
                for (Item it : ok) {
                    String cat = it.cat;
                    Long v = totaisCategoria.get(cat);
                    if (v != null) totaisCategoria.put(cat, Math.max(0, v - it.size));
                }
                reconstruirPastas();
                atualizarResumoArmazenamento();
                atualizarLista();
                String msg = "Liberado " + fmt(fLib);
                if (fFal > 0) msg += " — " + fFal + " não puderam ser apagados";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    // --------------------------------------------------------------- extras

    private void abrirArquivo(File f) {
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensao(f.getName()));
        final String tipo = mime == null ? "*/*" : mime;
        MediaScannerConnection.scanFile(getApplicationContext(),
                new String[]{f.getAbsolutePath()}, new String[]{tipo}, (path, uri) -> ui.post(() -> {
                    if (uri == null) {
                        Toast.makeText(this, "Não foi possível abrir esse arquivo", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(uri, tipo);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, f.getName()));
                    } catch (Exception e) {
                        Toast.makeText(this, "Nenhum app abre esse tipo de arquivo", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void abrirArmazenamentoSistema() {
        try {
            startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ------------------------------------------------------------ adaptador

    private class Adaptador extends BaseAdapter {
        @Override
        public int getCount() {
            return visiveis.size();
        }

        @Override
        public Object getItem(int i) {
            return visiveis.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            Linha l;
            if (convertView == null) {
                l = new Linha();
                convertView = l.raiz;
                convertView.setTag(l);
            } else {
                l = (Linha) convertView.getTag();
            }
            Item it = visiveis.get(pos);
            l.check.setVisibility(it.isDir ? View.GONE : View.VISIBLE);
            l.check.setChecked(it.checked);
            l.tamanho.setText(fmt(it.size));
            if (it.isDir) {
                l.nome.setText("📁 " + it.file.getName());
                l.detalhe.setText(relativo(it.file.getAbsolutePath()));
            } else {
                l.nome.setText(it.file.getName());
                String data = DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(it.modified));
                l.detalhe.setText(it.cat + " • " + data + " • " + relativo(it.file.getParent()));
            }
            return convertView;
        }
    }

    private class Linha {
        final LinearLayout raiz = new LinearLayout(MainActivity.this);
        final CheckBox check = new CheckBox(MainActivity.this);
        final TextView nome = new TextView(MainActivity.this);
        final TextView detalhe = new TextView(MainActivity.this);
        final TextView tamanho = new TextView(MainActivity.this);

        Linha() {
            raiz.setGravity(Gravity.CENTER_VERTICAL);
            raiz.setPadding(0, dp(6), 0, dp(6));
            check.setFocusable(false);
            check.setClickable(false);
            raiz.addView(check);

            LinearLayout textos = new LinearLayout(MainActivity.this);
            textos.setOrientation(LinearLayout.VERTICAL);
            nome.setTextSize(15);
            nome.setSingleLine(true);
            nome.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            detalhe.setTextSize(12);
            detalhe.setTextColor(Color.GRAY);
            detalhe.setSingleLine(true);
            detalhe.setEllipsize(android.text.TextUtils.TruncateAt.START);
            textos.addView(nome);
            textos.addView(detalhe);
            raiz.addView(textos, new LinearLayout.LayoutParams(0, -2, 1));

            tamanho.setTextSize(15);
            tamanho.setTypeface(Typeface.DEFAULT_BOLD);
            tamanho.setPadding(dp(8), 0, dp(4), 0);
            raiz.addView(tamanho);
        }
    }
}
