package se.anatom.ejbca.protocol;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.X509Certificate;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Hashtable;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;

/** A response message for scep (pkcs7).
*
* @version  $Id: ScepResponseMessage.java,v 1.3 2003-06-15 11:58:32 anatom Exp $
*/
public class  ScepResponseMessage implements IResponseMessage {

    private static Logger log = Logger.getLogger(ScepResponseMessage.class);

    /** The response message itself
     */
    private CMSSignedData signedData = null;
    /** status for the response
     */
    private int status = 0;
    /** Possible fail information in the response. Defaults to 'badRequest (2)'. 
     */
    private String failInfo = "2";
    /** Certificate to be in response message, not serialized
     */
    private transient Certificate cert = null;

    private transient X509Certificate signCert = null;
    private transient PrivateKey signKey = null;
    private transient X509Certificate encCert = null;
    private transient PrivateKey encKey = null;
        
    /** Sets the complete certificate in the response message.
     * @param cert certificate in the response message.
     */
    public void setCertificate(Certificate cert) {
        this.cert=cert;
    }
    /** Gets the response message in the default encoding format.
     * @return the response message in the default encoding format.
     */
    public byte[] getResponseMessage() throws IOException, CertificateEncodingException {
            return signedData.getEncoded();
    }
    /** Sets the status of the response message.
     * @param status status of the response.
     */
    public void setStatus(int status) {
        this.status = status;
    }
    /** Sets info about reason for failure.
     * @param failInfo reason for failure.
     */
    public void setFailInfo(String failInfo) {
        this.failInfo = failInfo;
    }
    /** Create encrypts and creates signatures as needed to produce a complete response message. 
     * If needed setSignKeyInfo and setEncKeyInfo must be called before this method.
     * After this is called the response message can be retrieved with getResponseMessage();
     * @return True if signature/encryption was successful, false if it failed, request should not be sent back i failed.
     * @throws IOException If input/output or encoding failed.
     * @throws InvalidKeyException If the key used for signing/encryption is invalid.
     * @throws NoSuchProviderException if there is an error with the Provider.
     * @throws NoSuchAlgorithmException if the signature on the request is done with an unhandled algorithm.
     * @see #setSignKeyInfo()
     * @see #setEncKeyInfo()
     */
    public boolean create() throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException {
        boolean ret = false;
        try {
            // Add the issued certificate to the signed portion of the CMS (as signer, degenerate case)
            ArrayList certList = new ArrayList();
            if (status == IResponseMessage.STATUS_OK) {
                certList.add(cert);
            }
            certList.add(signCert);
            CertStore certs = CertStore.getInstance("Collection", new CollectionCertStoreParameters(certList), "BC");
            // Create the signed CMS message to be contained inside the envelope
            CMSProcessable msg = null;
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            gen.addSigner(signKey, signCert, CMSSignedDataGenerator.DIGEST_SHA1);
            gen.addCertificatesAndCRLs(certs);
            CMSSignedData s = gen.generate(msg, true, "BC");
            // Envelope the CMS message
            CMSEnvelopedDataGenerator edGen = new CMSEnvelopedDataGenerator();
            if (status == IResponseMessage.STATUS_OK) {
                edGen.addKeyTransRecipient((X509Certificate)cert);
            }
            CMSEnvelopedData ed = edGen.generate(new CMSProcessableByteArray(s.getEncoded()), 
                    CMSEnvelopedDataGenerator.DES_EDE3_CBC, "BC");
             // Create the outermost signed data
            msg = new CMSProcessableByteArray(ed.getEncoded());
            CMSSignedDataGenerator gen1 = new CMSSignedDataGenerator();
            // add status, transactionId, sender- and recipientNonce
            Hashtable attributes = new Hashtable();
            // TransactionId
            DERObjectIdentifier oid = new DERObjectIdentifier(ScepRequestMessage.id_transId);
            DERSet value = new DERSet(new DERPrintableString("foo"));
            Attribute attr = new Attribute(oid, value);
            attributes.put(attr.getAttrType(), attr);
            // status
            oid = new DERObjectIdentifier(ScepRequestMessage.id_pkiStatus);
            if (status == IResponseMessage.STATUS_OK) {
                //value = new DERSet(new DERPrintableString("SUCCESS"));
                value = new DERSet(new DERPrintableString("0"));
            } else {
                //value = new DERSet(new DERPrintableString("FAILURE"));
                value = new DERSet(new DERPrintableString("2"));
            }
            attr = new Attribute(oid, value);
            attributes.put(attr.getAttrType(), attr);
            if (status == IResponseMessage.STATUS_FAILED) {
                oid = new DERObjectIdentifier(ScepRequestMessage.id_failInfo);
                value = new DERSet(new DERPrintableString(failInfo));
                attr = new Attribute(oid, value);
                attributes.put(attr.getAttrType(), attr);
            } 
            // senderNonce
            oid = new DERObjectIdentifier(ScepRequestMessage.id_senderNonce);
            value = new DERSet(new DEROctetString("1234567890123456".getBytes()));
            attr = new Attribute(oid, value);
            attributes.put(attr.getAttrType(), attr);
            // recipientNonce
            oid = new DERObjectIdentifier(ScepRequestMessage.id_recipientNonce);
            value = new DERSet(new DEROctetString("1234567890123456".getBytes()));
            attr = new Attribute(oid, value);
            attributes.put(attr.getAttrType(), attr);
            // Put our signer info and all newly generated attributes
            gen1.addSigner(signKey, signCert, CMSSignedDataGenerator.DIGEST_SHA1, new AttributeTable(attributes), null);
            
            signedData = gen.generate(msg, true, "BC");
            if (signedData != null) {
                ret = true;
            }
        } catch (InvalidAlgorithmParameterException e) {
            log.error("Error creating CertStore: ", e);
        } catch (CertStoreException e) {
            log.error("Error creating CertStore: ", e);
        } catch (CMSException e) {
            log.error("Error creating CMS message: ", e);
        }
        
        // TODO: dont forget transactionId
        // TODO: don't forget sender- and recipientNonce
        
        return ret;
    }
    /** indicates if this message needs recipients public and private key to sign.
     * If this returns true, setSignKeyInfo() should be called.
     * @return True if public and private key is needed.
     */
    public boolean requireSignKeyInfo() {
        return true;
    }
    /** indicates if this message needs recipients public and private key to encrypt.
     * If this returns true, setEncKeyInfo() should be called.
     * @return True if public and private key is needed.
     */
    public boolean requireEncKeyInfo() {
        return true;
    }
    /** Sets the public and private key needed to sign the message. Must be set if requireSignKeyInfo() returns true.
     * @see #requireSignKeyInfo()
     *
     * @param cert certificate containing the public key.
     * @param key private key.
     */
    public void setSignKeyInfo(X509Certificate cert, PrivateKey key) {
        signCert = cert;
        signKey = key;
    }
    /** Sets the public and private key needed to encrypt the message. Must be set if requireEncKeyInfo() returns true.
     * @see #requireEncKeyInfo()
     *
     * @param cert certificate containing the public key.
     * @param key private key.
     */
    public void setEncKeyInfo(X509Certificate cert, PrivateKey key) {
        encCert = cert;
        encKey = key;
    }
}
