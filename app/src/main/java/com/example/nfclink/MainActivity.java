package com.example.nfclink;

import android.content.Context;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;

// IMPORTANTE: Implementamos la interfaz para poder enviar datos
public class MainActivity extends AppCompatActivity implements NfcAdapter.CreateNdefMessageCallback {

    // Componentes de la interfaz
    private EditText etName, etPhone;
    private TextView tvReceivedInfo;
    private NfcAdapter nfcAdapter;

    // Variables para almacenar datos
    private String lastReceivedVCard = ""; // Lo que recibimos de otro cel
    private String nameToSend = "";        // Lo que vamos a enviar
    private String phoneToSend = "";
    private boolean isReadyToSend = false; // Bandera de seguridad

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Vincular vistas con el XML
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        tvReceivedInfo = findViewById(R.id.tvReceivedInfo);

        Button btnPrepare = findViewById(R.id.btnPrepare); // Botón Naranja
        Button btnExport = findViewById(R.id.btnExport);   // Botón Azul
        Button btnSaveToContacts = findViewById(R.id.btnSaveToContacts); // Botón Verde (si lo agregaste)

        // 2. Inicializar NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "ERROR: Este celular no tiene chip NFC", Toast.LENGTH_LONG).show();
            // No cerramos la app para que al menos puedas probar los botones
        } else {
            // Configurar el callback de envío (Android Beam)
            nfcAdapter.setNdefPushMessageCallback(this, this);
        }

        // 3. Configurar acciones de botones

        // BOTÓN 1: Preparar Envío (Cierra teclado y fija variables)
        btnPrepare.setOnClickListener(v -> prepareMessageForSending());

        // BOTÓN 2: Exportar archivo
        btnExport.setOnClickListener(v -> exportVCardToFile());

        // BOTÓN 3: Guardar en Contactos (Opcional si agregaste el botón)
        if (btnSaveToContacts != null) {
            btnSaveToContacts.setOnClickListener(v -> saveToPhonebook());
        }
    }

    // ----------------------------------------------------------------
    // LÓGICA DE ENVÍO (Emisor)
    // ----------------------------------------------------------------

    // Método llamado por el botón naranja
    private void prepareMessageForSending() {
        String name = etName.getText().toString();
        String phone = etPhone.getText().toString();

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "⚠️ Escribe nombre y teléfono primero", Toast.LENGTH_SHORT).show();
            isReadyToSend = false;
            return;
        }

        // Guardamos los datos en memoria segura
        nameToSend = name;
        phoneToSend = phone;
        isReadyToSend = true;

        // TRUCO CLAVE: Cerrar el teclado para evitar fallos en Android Beam
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

        Toast.makeText(this, "✅ Datos listos. ¡Ahora toca el otro teléfono!", Toast.LENGTH_LONG).show();
    }

    // Método que Android llama automáticamente cuando tocas otro celular
    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        // Si el usuario olvidó presionar "Preparar", intentamos leer al vuelo
        if (!isReadyToSend) {
            nameToSend = etName.getText().toString();
            phoneToSend = etPhone.getText().toString();
        }

        // Construir la VCard manual
        String vCardData = "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "N:" + nameToSend + ";;;\n" +
                "FN:" + nameToSend + "\n" +
                "TEL;TYPE=CELL:" + phoneToSend + "\n" +
                "END:VCARD";

        // Empaquetar como mensaje NDEF
        NdefRecord record = NdefRecord.createMime(
                "text/x-vcard", vCardData.getBytes(Charset.forName("UTF-8"))
        );

        return new NdefMessage(record);
    }

    // ----------------------------------------------------------------
    // LÓGICA DE RECEPCIÓN (Receptor)
    // ----------------------------------------------------------------

    @Override
    public void onResume() {
        super.onResume();
        // Si la app se abrió por un toque NFC, procesamos el intent
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
            // Leemos el payload del primer registro
            String vCardContent = new String(msg.getRecords()[0].getPayload());

            // Guardamos en variable global para poder exportar/guardar después
            lastReceivedVCard = vCardContent;

            // Actualizamos la pantalla
            tvReceivedInfo.setText(parseVcardName(vCardContent));
            Toast.makeText(this, "📳 ¡Contacto Recibido!", Toast.LENGTH_LONG).show();
        }
    }

    // Extraer solo el nombre para mostrarlo bonito en el TextView
    private String parseVcardName(String vCard) {
        try {
            if (vCard.contains("FN:")) {
                int start = vCard.indexOf("FN:") + 3;
                int end = vCard.indexOf("\n", start);
                if (end == -1) end = vCard.length();
                return "Recibido: " + vCard.substring(start, end).trim();
            }
        } catch (Exception e) {
            return "Error al leer nombre";
        }
        return "Datos recibidos (Formato Raw)";
    }

    // ----------------------------------------------------------------
    // FUNCIONES EXTRA (Exportar y Guardar)
    // ----------------------------------------------------------------

    private void exportVCardToFile() {
        String dataToSave = lastReceivedVCard;

        // Si no hemos recibido nada, usamos tus propios datos para probar que el archivo se crea
        if (dataToSave.isEmpty()) {
            dataToSave = "BEGIN:VCARD\nVERSION:3.0\nFN:" + etName.getText() + "\nTEL:" + etPhone.getText() + "\nEND:VCARD";
            Toast.makeText(this, "Exportando tus propios datos (Prueba)", Toast.LENGTH_SHORT).show();
        }

        try {
            File path = getExternalFilesDir(null);
            File file = new File(path, "contacto_nfc.vcf");

            FileWriter writer = new FileWriter(file);
            writer.append(dataToSave);
            writer.flush();
            writer.close();

            Toast.makeText(this, "📁 Guardado en: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error de escritura: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToPhonebook() {
        String dataToSave = lastReceivedVCard;

        // Truco para probar si no has recibido nada
        if (dataToSave.isEmpty()) {
            dataToSave = "BEGIN:VCARD\nFN:" + etName.getText() + "\nTEL:" + etPhone.getText() + "\nEND:VCARD";
        }

        // Parseo simple para el Intent
        String name = parseVcardName(dataToSave).replace("Recibido: ", "");
        String phone = "";

        if (dataToSave.contains("TEL")) {
            int start = dataToSave.indexOf("TEL");
            int sep = dataToSave.indexOf(":", start);
            int end = dataToSave.indexOf("\n", sep);
            if (sep != -1 && end != -1) phone = dataToSave.substring(sep + 1, end).trim();
        }

        // Abrir la app de Contactos nativa
        Intent intent = new Intent(ContactsContract.Intents.Insert.ACTION);
        intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);
        intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
        intent.putExtra(ContactsContract.Intents.Insert.PHONE, phone);

        startActivity(intent);
    }
}