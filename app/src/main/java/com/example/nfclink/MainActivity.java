package com.example.nfclink;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;

public class MainActivity extends AppCompatActivity {

    private EditText etName, etPhone;
    private TextView tvReceivedInfo;
    private NfcAdapter nfcAdapter;
    private String lastReceivedVCard = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Asignar las variables (Asegúrate que los IDs coincidan con tu XML)
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        tvReceivedInfo = findViewById(R.id.tvReceivedInfo);
        Button btnExport = findViewById(R.id.btnExport);

        // 2. Iniciar el adaptador
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC no disponible", Toast.LENGTH_LONG).show();
            return;
        }

        nfcAdapter.setNdefPushMessageCallback(MainActivity.this, MainActivity.this);

        btnExport.setOnClickListener(v-> exportVCard());
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        String name = etName.getText().toString();
        String phone = etPhone.getText().toString();

        String vCardData = "BEGIN:VCARD\n" + "VERSION:3.0\n" + "N:" + name + ";;;\n" + "FN:" + name + "\n" + "TEL;CELL:" + phone+ "\n" + "END:VCARD";
        NdefRecord record = new NdefRecord(
                NdefRecord.TNF_MIME_MEDIA,
                "text/x-vcard".getBytes(Charset.forName("US-ASCII")),
                new byte[0],
                vCardData.getBytes(Charset.forName("UTF-8"))
        );

        return new NdefMessage(record);
    }

    @Override
    public void onResume(){
        super.onResume();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); //Actualiza el intent
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            processIntent(intent);
        }
    }

    //Extraer texto del mensaje NFC
    private void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (rawMsgs != null) {
            NdefMessage msg = (NdefMessage) rawMsgs[0];
            //Se asume que el primer registro es nuestra vCard
            String vCardContent = new String(msg.getRecords()[0].getPayload());

            lastReceivedVCard = vCardContent;
            tvReceivedInfo.setText(parseVcardName(vCardContent));
            Toast.makeText(this, "Contacto recibido con éxito!", Toast.LENGTH_SHORT).show();
        }
    }

    //Metodo para mostrar algo legible en la pantalla
    private String parseVcardName(String vCard) {
        if (vCard.contains("FN:")) {
            int start = vCard.indexOf("FN") + 3;
            int end = vCard.indexOf("\n", start);
            return "Nombre: " + vCard.substring(start, end);
        }
        return "Datos de vCard recibidos (formato raw)";
    }

    //Guardar el contacto recibido en un archivo
    private void exportVCard() {
        if (lastReceivedVCard.isEmpty()) {
            Toast.makeText(this, "No exuste contacto para exportar", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            //Se guarda en el directorio de documentos de la app
            File path = getExternalFilesDir(null);
            File file = new File(path, "contacto_recibido.vcf");

            FileWriter writer = new FileWriter(file);
            writer.append(lastReceivedVCard);
            writer.flush();
            writer.close();

            Toast.makeText(this, "Guardado en: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show();
        }
    }
}