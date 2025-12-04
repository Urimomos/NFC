package com.example.nfclink;

import android.app.PendingIntent; // Importación necesaria
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract; // Necesario para guardar en agenda
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;

// CORRECCIÓN 1: Agregar "implements NfcAdapter.CreateNdefMessageCallback"
public class MainActivity extends AppCompatActivity implements NfcAdapter.CreateNdefMessageCallback {

    private EditText etName, etPhone;
    private TextView tvReceivedInfo;
    private NfcAdapter nfcAdapter;
    private String lastReceivedVCard = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        tvReceivedInfo = findViewById(R.id.tvReceivedInfo);
        Button btnExport = findViewById(R.id.btnExport);

        // Botón adicional para guardar en la agenda del teléfono
        Button btnSaveToContacts = findViewById(R.id.btnSaveToContacts); // Asegúrate de añadir este botón en tu XML o bórralo si no lo quieres

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC no disponible", Toast.LENGTH_LONG).show();
            return;
        }

        // Esto ahora funcionará porque agregamos el "implements" arriba
        nfcAdapter.setNdefPushMessageCallback(this, this);

        btnExport.setOnClickListener(v -> exportVCardToFile());

        // CORRECCIÓN 2: Lógica para guardar en la agenda del teléfono (Requisito: Guardar contactos recibidos)
        if(btnSaveToContacts != null) {
            btnSaveToContacts.setOnClickListener(v -> saveToPhonebook());
        }
    }

    // Lógica para ENVIAR (Cuando tocas otro cel)
    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        String name = etName.getText().toString();
        String phone = etPhone.getText().toString();

        String vCardData = "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "N:" + name + ";;;\n" +
                "FN:" + name + "\n" +
                "TEL;TYPE=CELL:" + phone + "\n" +
                "END:VCARD";

        NdefRecord record = NdefRecord.createMime(
                "text/x-vcard", vCardData.getBytes(Charset.forName("UTF-8"))
        );

        return new NdefMessage(record);
    }

    @Override
    public void onResume(){
        super.onResume();
        // Verificar si la app se abrió por NFC
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            processIntent(intent);
        }
    }

    private void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (rawMsgs != null) {
            NdefMessage msg = (NdefMessage) rawMsgs[0];
            String vCardContent = new String(msg.getRecords()[0].getPayload());

            lastReceivedVCard = vCardContent;
            tvReceivedInfo.setText(parseVcardName(vCardContent));
            Toast.makeText(this, "Contacto leído correctamente", Toast.LENGTH_SHORT).show();

            // Opcional: Intentar guardar automáticamente al recibir
            // saveToPhonebook();
        }
    }

    private String parseVcardName(String vCard) {
        try {
            if (vCard.contains("FN:")) {
                int start = vCard.indexOf("FN:") + 3;
                int end = vCard.indexOf("\n", start);
                if(end == -1) end = vCard.length(); // Por si es la última línea
                return "Recibido: " + vCard.substring(start, end).trim();
            }
        } catch (Exception e) {
            return "Error al leer nombre";
        }
        return "Datos vCard sin nombre legible";
    }

    // OPCIÓN A: Requisito "Exportar a archivo VCard"
    private void exportVCardToFile() {
        if (lastReceivedVCard.isEmpty()) {
            Toast.makeText(this, "No hay contacto recibido para exportar", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File path = getExternalFilesDir(null);
            File file = new File(path, "contacto_recibido.vcf");

            FileWriter writer = new FileWriter(file);
            writer.append(lastReceivedVCard);
            writer.flush();
            writer.close();

            Toast.makeText(this, "Archivo guardado en: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error de escritura", Toast.LENGTH_SHORT).show();
        }
    }

    // OPCIÓN B: Requisito "Guardar contactos recibidos" (En la agenda del cel)
    private void saveToPhonebook() {
        if (lastReceivedVCard.isEmpty()) return;

        // Parseo simple para obtener nombre y teléfono
        String name = parseVcardName(lastReceivedVCard).replace("Recibido: ", "");
        String phone = "";

        if (lastReceivedVCard.contains("TEL;")) {
            int start = lastReceivedVCard.indexOf("TEL;");
            int separator = lastReceivedVCard.indexOf(":", start);
            int end = lastReceivedVCard.indexOf("\n", separator);
            if(end != -1 && separator != -1) {
                phone = lastReceivedVCard.substring(separator + 1, end).trim();
            }
        }

        // Intent nativo de Android para guardar contacto
        Intent intent = new Intent(ContactsContract.Intents.Insert.ACTION);
        intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);
        intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
        intent.putExtra(ContactsContract.Intents.Insert.PHONE, phone);
        startActivity(intent);
    }
}