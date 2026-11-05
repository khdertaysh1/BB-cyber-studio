package studio.bb.cyber;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

/** One explicitly saved workspace, encrypted with a non-exportable Android Keystore key. */
final class WorkspaceStore {
    private static final String ALIAS = "bb.workspace.v1";
    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS,null);
    }
    static void save(Context context, String content) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        File target = new File(context.getFilesDir(),"workspace.enc");
        File temp = new File(context.getFilesDir(),"workspace.tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(cipher.getIV()); output.write(cipher.doFinal(content.getBytes(StandardCharsets.UTF_8))); output.getFD().sync();
        }
        if (!temp.renameTo(target)) throw new java.io.IOException("Cannot replace saved workspace");
    }
    static String load(Context context) throws Exception {
        File file = new File(context.getFilesDir(),"workspace.enc");
        if (!file.exists()) return null;
        if (file.length() < 28 || file.length() > 2*1024*1024) throw new java.io.IOException("Invalid workspace file");
        byte[] all = new byte[(int)file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset=0, count;
            while(offset<all.length && (count=input.read(all,offset,all.length-offset))!=-1) offset+=count;
            if(offset!=all.length) throw new java.io.IOException("Incomplete workspace");
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,all,0,12));
        return new String(cipher.doFinal(all,12,all.length-12),StandardCharsets.UTF_8);
    }
    static void delete(Context context) throws Exception {
        File file = new File(context.getFilesDir(),"workspace.enc");
        if(file.exists() && !file.delete()) throw new java.io.IOException("Cannot delete workspace");
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null); ks.deleteEntry(ALIAS);
    }
}
