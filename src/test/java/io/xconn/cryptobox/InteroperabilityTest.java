package io.xconn.cryptobox;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import com.iwebpp.crypto.TweetNaclFast;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class InteroperabilityTest {

    private static final byte[] publicKey = Hex.decode("e54e7c4f75ea1cba7b276711ad2e88e7ac963502906724b86794d115df85114b");
    private static final byte[] privateKey = Hex.decode("28cf2aaeca5db014927f3956ac3c32141b9a08164367326b549b36bc81c3ac48");

    @Test
    public void secretBoxTest() {
        byte[] message = "Hello, World!".getBytes();
        byte[] nonce = Util.generateRandomBytesArray(Util.NONCE_SIZE);

        // encrypt using TweetNaCl
        TweetNaclFast.SecretBox box = new TweetNaclFast.SecretBox(privateKey);
        byte[] ct = box.box(message, nonce);

        byte[] ciphertext = new byte[nonce.length + ct.length];
        System.arraycopy(nonce, 0, ciphertext, 0, nonce.length);
        System.arraycopy(ct, 0, ciphertext, nonce.length, ct.length);

        //  decrypt using SecretBox
        byte[] plainText = SecretBox.boxOpen(ciphertext, privateKey);

        assertArrayEquals(message, plainText);

        // encrypt using SecretBox
        byte[] cipherText = SecretBox.box(message, privateKey);

        byte[] nonce1 = Arrays.copyOfRange(cipherText, 0, Util.NONCE_SIZE);
        byte[] encryptedMessage = Arrays.copyOfRange(cipherText, Util.NONCE_SIZE, cipherText.length);

        // decrypt using TweetNaCl
        byte[] decryptedMessage = box.open(encryptedMessage, nonce1);
        assertArrayEquals(message, decryptedMessage);
    }

    @Test
    public void sealedBoxTest() throws GeneralSecurityException {
        byte[] message = "Hello, World!".getBytes();

        // encrypt using TweetNaCl
        byte[] ct = SealedBoxNaCl.crypto_box_seal(message, publicKey);

        // decrypt using SealedBox
        byte[] plainText = SealedBox.sealOpen(ct, privateKey);

        assertArrayEquals(message, plainText);

        // encrypt using SealedBox
        byte[] cipherText = SealedBox.seal(message, publicKey);

        // decrypt using TweetNaCl
        byte[] plaintext = SealedBoxNaCl.crypto_box_seal_open(cipherText, publicKey, privateKey);

        assertArrayEquals(message, plaintext);
    }

    /**
     * An implementation SealedBox using TweetNaCl.
     * Taken from https://stackoverflow.com/a/42456750
     */
    static class SealedBoxNaCl {
        static byte[] crypto_box_seal(byte[] clearText, byte[] receiverPubKey) throws GeneralSecurityException {
            // create ephemeral keypair for sender
            TweetNaclFast.Box.KeyPair ephkeypair = TweetNaclFast.Box.keyPair();
            // create nonce
            byte[] nonce = SealedBox.createNonce(ephkeypair.getPublicKey(), receiverPubKey);
            TweetNaclFast.Box box = new TweetNaclFast.Box(receiverPubKey, ephkeypair.getSecretKey());
            byte[] ciphertext = box.box(clearText, nonce);
            if (ciphertext == null) throw new GeneralSecurityException("could not create box");

            byte[] sealedbox = new byte[ciphertext.length + Util.PUBLIC_KEY_BYTES];
            byte[] ephpubkey = ephkeypair.getPublicKey();

            System.arraycopy(ephpubkey, 0, sealedbox, 0, Util.PUBLIC_KEY_BYTES);
            System.arraycopy(ciphertext, 0, sealedbox, 32, ciphertext.length);

            return sealedbox;
        }

        public static byte[] crypto_box_seal_open(byte[] c, byte[] pk, byte[] sk) throws GeneralSecurityException {
            if (c.length < Util.PUBLIC_KEY_BYTES + Util.MAC_SIZE)
                throw new IllegalArgumentException("Ciphertext too short");

            byte[] pksender = Arrays.copyOfRange(c, 0, Util.PUBLIC_KEY_BYTES);
            byte[] ciphertextwithmac = Arrays.copyOfRange(c, Util.PUBLIC_KEY_BYTES, c.length);
            byte[] nonce = SealedBox.createNonce(pksender, pk);

            TweetNaclFast.Box box = new TweetNaclFast.Box(pksender, sk);
            byte[] cleartext = box.open(ciphertextwithmac, nonce);
            if (cleartext == null) throw new GeneralSecurityException("could not open box");
            return cleartext;
        }
    }
}
