package DNA.sdk.wallet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.alibaba.fastjson.JSON;

import DNA.Fixed8;
import DNA.Helper;
import DNA.UInt160;
import DNA.UInt256;
import DNA.Core.AssetType;
import DNA.Core.Blockchain;
import DNA.Core.IssueTransaction;
import DNA.Core.RecordTransaction;
import DNA.Core.RecordType;
import DNA.Core.RegisterTransaction;
import DNA.Core.SignatureContext;
import DNA.Core.Transaction;
import DNA.Core.TransactionAttribute;
import DNA.Core.TransactionAttributeUsage;
import DNA.Core.TransactionInput;
import DNA.Core.TransactionOutput;
import DNA.Core.TransferTransaction;
import DNA.Implementations.Blockchains.Rest.RestBlockchain;
import DNA.Implementations.Wallets.Mysql.WebWallet;
import DNA.Network.Rest.RestException;
import DNA.Network.Rest.RestNode;
import DNA.Wallets.Account;
import DNA.Wallets.Contract;
import DNA.Wallets.Wallet;
import DNA.sdk.info.account.AccountAsset;
import DNA.sdk.info.account.AccountInfo;
import DNA.sdk.info.account.Asset;
import DNA.sdk.info.asset.AssetInfo;
import DNA.sdk.info.transaction.TransactionInfo;
import DNA.sdk.info.transaction.TxInputInfo;
import DNA.sdk.info.transaction.TxOutputInfo;


/**
 * 账户管理器(mysql存储)
 * 
 * @author 12146
 *
 */
public class MysqlWalletManager {
	private String action = "sendrawtransaction",version = "v001",type = "t001";
	private WebWallet uw;
	private RestNode restNode;
	
	public static MysqlWalletManager getWallet(String policy, String url, String accessToken) {
		MysqlWalletManager wm = new MysqlWalletManager();
		wm.initRestNode(url);
		wm.setAccessToken(accessToken);
		wm.initBlockchain();
		wm.initWallet(policy);
		return wm;
	}
	
	public WebWallet getWallet(){
		return uw;
	}
	private MysqlWalletManager() {
	}
	private void initWallet(String policy) {
		uw = WebWallet.create(policy, "0x123456");
		uw.start();
	}
	private void initRestNode(String url) {
		restNode = new RestNode(url);
	}
	public void initBlockchain() {
		Blockchain.register(new RestBlockchain(restNode));
	}
	
	
	/**
	 * 创建单个账户
	 */
	public String createAccount() {
		return createAddress();
	}
	
	/**
	 * 创建多个账户
	 */
	public List<String> createAccount(int n) {
		return Stream.generate(() -> createAddress()).limit(n).collect(Collectors.toList());
	}
	// 
	private String createAddress() {
		return uw.getContract(Contract.createSignatureContract(uw.createAccount().publicKey).address()).address();
	}
	
	public String address2UInt160(String address) {
		return Wallet.toScriptHash(address).toString();
	}
	public String uint1602Address(String uint160) {
		return Wallet.toAddress(UInt160.parse(uint160));
	}
	
	public int blockHeight() {
		try {
			return restNode.getBlockHeight();
		} catch (RestException e) {
			throw new RuntimeException(e);
		}
	}
	
	
	public void setAuthType(String authType) {
		this.restNode.setAuthType(authType);
	}
	
	public void setAccessToken(String accessToken) {
		this.restNode.setAccessToken(accessToken);
	}
	
	public void setVersion(String version, String type) {
		this.version = version;
		this.type = type;
	}
	
	/**
	 * 注册资产
	 * @throws Exception 
	 */
	public String reg() throws Exception {// in-null,out-null
		throw new UnsupportedOperationException();
	}
	public String regToken(String issuer, String name, int amount, String desc) throws Exception {
		return regToken(getAccount(issuer), name, amount, desc);
	}
	public String regShare(String issuer, String name, int amount, String desc) throws Exception {
		return regShare(getAccount(issuer), name, amount, desc);
	}
	public String regToken(Account acc, String assetName, int assetAmount, String txDesc) throws Exception {
		return reg(getRegTx(acc, assetName, assetAmount, txDesc, AssetType.Token));
	}
	public String regShare(Account acc, String assetName, int assetAmount, String txDesc) throws Exception {
		return reg(getRegTx(acc, assetName, assetAmount, txDesc, AssetType.Share));
	}
	private String reg(RegisterTransaction regTx) throws Exception {
		RegisterTransaction signedTx4Reg = uw.makeTransaction(regTx, Fixed8.ZERO);
		SignatureContext context = new SignatureContext(signedTx4Reg);
		boolean f1 = uw.sign(context);
		if(context.isCompleted()){
			signedTx4Reg.scripts = context.getScripts();
		}
		uw.saveTransaction(signedTx4Reg);
		String txHex = Helper.toHexString(signedTx4Reg.toArray());;
		boolean f2 = restNode.sendRawTransaction(action, version, type, txHex);
		
		String txid = signedTx4Reg.hash().toString();
		System.out.println("reg.sign:"+f1+",rst:"+f2+",txid:"+ txid);
		if(f2) {
			wait(uw,txid); // 等待生效
		}
		return txid;
	}
	
	/**
	 * 分发资产
	 * @throws Exception 
	 */
	public String iss() {//in-null
		throw new UnsupportedOperationException();
	}
	public String iss(String sendAddr, String assetid, int amount, String recvAddr, String desc) throws Exception {
		return iss(getIssTx(assetid, amount, recvAddr, desc), getAddress(sendAddr));
	}
	private String iss(IssueTransaction issueTx, UInt160 from) throws Exception {
		IssueTransaction signedTx4Iss = uw.makeTransaction(issueTx, Fixed8.ZERO, from);
		SignatureContext context4Iss = new SignatureContext(signedTx4Iss);
		boolean f3 = uw.sign(context4Iss);
		if(context4Iss.isCompleted()){
			signedTx4Iss.scripts = context4Iss.getScripts();
		}
		uw.saveTransaction(signedTx4Iss);
		String txHex = Helper.toHexString(signedTx4Iss.toArray());;
		boolean f4 = restNode.sendRawTransaction(action, version, type, txHex);
		
		String txid4Iss = signedTx4Iss.hash().toString();
		System.out.println("iss.sign:"+f3+",rst:"+f4+",txid:"+ txid4Iss);
		if(f4) {
			wait(uw,txid4Iss); // 等待生效
		}
		return txid4Iss;
	}
	/**
	 * 转账
	 */
	public String trf(String sendAddr, String assetid, int amount, String recvAddr, String desc) throws Exception {
		return trf(getTrfTx(assetid, amount, recvAddr, desc), getAddress(sendAddr));
	}
	private String trf(TransferTransaction trfTx, UInt160 from) throws Exception {
		TransferTransaction signedTx4Trf = uw.makeTransaction(trfTx, Fixed8.ZERO, from);
		SignatureContext context4Trf = new SignatureContext(signedTx4Trf);
		boolean f5 = uw.sign(context4Trf);
		if(context4Trf.isCompleted()){
			signedTx4Trf.scripts = context4Trf.getScripts();
		}
		uw.saveTransaction(signedTx4Trf);
		String txHex = Helper.toHexString(signedTx4Trf.toArray());;
		boolean f6 = restNode.sendRawTransaction(action, version, type, txHex);
		
		String txid4Trf = signedTx4Trf.hash().toString();
		System.out.println("trf.sign:"+f5+",rst:"+f6+",txid:"+ txid4Trf);
		if(f6) {
			wait(uw,txid4Trf); // 等待生效
		}
		return txid4Trf;
	}
	
	/**
	 * 存证
	 */
	public String storeCert(String data, String desc) throws Exception {
		RecordTransaction tx = getRcdTx(data, desc);
		String txHex = Helper.toHexString(tx.toArray());;
		boolean f = restNode.sendRawTransaction(action, version, type, txHex);
		
		String txid = tx.hash().toString();
		System.out.println("rcd.sign:null, rst:"+f+",txid:"+txid);
		return txid;
	}
	
	/**
	 * 取证
	 */
	public String queryCert(String txid) throws Exception {
		Transaction tx = restNode.getRawTransaction(txid);
		if(tx instanceof RecordTransaction) {
			RecordTransaction rr = (RecordTransaction) tx;
			return new String(rr.recordData);
		}
		return null;
	}
	
	
	// 获取账户
	public Account getAccount(String address) {
		return uw.getAccount(uw.getContract(address).publicKeyHash);
	}
	// 获取地址
	public UInt160 getAddress(String address) {
		return Wallet.toScriptHash(address);
	}
	// 等待Tx生效
	private void wait(Wallet uw, String txid) {
		int count = 13;	// 最长等待1分钟
		while(--count > 0) {
			Map<Transaction, Integer> txs = uw.LoadTransactions();
			if(txs != null && txs.keySet().stream().filter(p -> txs.get(p).intValue() > 1).filter(p -> p.hash().toString().equals(txid)).count() == 1) {
				System.out.println("sync over, txid:"+txid);
				break;
			}
			try {
				Thread.sleep(1000*5);
			} catch (InterruptedException e) {
			}
			System.out.println("sleep.....5s");
		}
		System.out.println("sync timeout,txid:"+txid);
	}
	
	private RegisterTransaction getRegTx(Account acc, String assetName, int assetAmount, String txDesc, AssetType assetType) {
		RegisterTransaction tx = new RegisterTransaction();
		
		tx.precision = 0;						// 精度
		tx.assetType = AssetType.Share;			// 资产类型
		tx.recordType = RecordType.UTXO;			// 记账模式
		tx.nonce = (int)Math.random()*10;		// 随机数
		
		tx.assetType = assetType ;	
		tx.name = assetName;	
		tx.amount = Fixed8.parse(String.valueOf(assetAmount));	
		tx.issuer = acc.publicKey;	
		tx.admin = Wallet.toScriptHash(Contract.createSignatureContract(acc.publicKey).address()); 
		tx.outputs = new TransactionOutput[0];
		if(txDesc != null && txDesc.length() > 0) {
			tx.attributes = new TransactionAttribute[1];
			tx.attributes[0] = new TransactionAttribute();
			tx.attributes[0].usage = TransactionAttributeUsage.DescriptionUrl;
			tx.attributes[0].data = (txDesc+new Date().toString()).getBytes();
		}
		return tx;
	}
	private IssueTransaction getIssTx(String assetId, int assetAmount, String recvAddr, String txDesc) {
		IssueTransaction tx = new IssueTransaction();
		tx.outputs = new TransactionOutput[1];
		tx.outputs[0] = new TransactionOutput();
		tx.outputs[0].assetId = UInt256.parse(assetId);
		tx.outputs[0].value = Fixed8.parse(String.valueOf(assetAmount));
		tx.outputs[0].scriptHash = Wallet.toScriptHash(recvAddr);
		if(txDesc != null && txDesc.length() > 0) {
			tx.attributes = new TransactionAttribute[1];
			tx.attributes[0] = new TransactionAttribute();
			tx.attributes[0].usage = TransactionAttributeUsage.DescriptionUrl;
			tx.attributes[0].data = (txDesc+new Date().toString()).getBytes();
		}
		return tx;
	}
	
	private TransferTransaction getTrfTx(String assetId, int assetAmount, String recvAddr, String txDesc) {
		TransferTransaction tx = new TransferTransaction();
		tx.outputs = new TransactionOutput[1];
		tx.outputs[0] = new TransactionOutput();
		tx.outputs[0].assetId = UInt256.parse(assetId);
		tx.outputs[0].value = Fixed8.parse(String.valueOf(assetAmount));
		tx.outputs[0].scriptHash = Wallet.toScriptHash(recvAddr);
		if(txDesc != null && txDesc.length() > 0) {
			tx.attributes = new TransactionAttribute[1];
			tx.attributes[0] = new TransactionAttribute();
			tx.attributes[0].usage = TransactionAttributeUsage.DescriptionUrl;
			tx.attributes[0].data = (txDesc+new Date().toString()).getBytes();
		}
		return tx;
	}
	private RecordTransaction getRcdTx(String data, String txDesc) {
		RecordTransaction rcdTx = new RecordTransaction();
		rcdTx.recordType = "";
		rcdTx.recordData = data.getBytes();
		rcdTx.outputs = new TransactionOutput[0];
		rcdTx.inputs = new TransactionInput[0];
		rcdTx.attributes = new TransactionAttribute[0];
		if(txDesc != null && txDesc.length() > 0) {
			rcdTx.attributes = new TransactionAttribute[1];
			rcdTx.attributes[0] = new TransactionAttribute();
			rcdTx.attributes[0].usage = TransactionAttributeUsage.DescriptionUrl;
			rcdTx.attributes[0].data = (txDesc+new Date().toString()).getBytes();
		}
		return rcdTx;
	}
	
	public AccountInfo getAccountInfo(String address) {
		AccountInfo info = new AccountInfo();
		Contract con = uw.getContract(address);
		Account acc = uw.getAccountByScriptHash(Wallet.toScriptHash(address));
		info.address = con.address();
		info.pubkey = Helper.toHexString(acc.publicKey.getEncoded(true));
		info.prikey = Helper.toHexString(acc.privateKey);
		info.priwif = acc.export();
		info.pkhash = acc.publicKeyHash.toString();
		return info;
	}

	public AccountAsset getAccountAsset(String address) {
		AccountAsset asset = new AccountAsset();
		Contract con = uw.getContract(address);
		asset.address = con.address();
		asset.canUseAssets = new ArrayList<Asset>();
		asset.freezeAssets = new ArrayList<Asset>();
		Arrays.stream(uw.findUnspentCoins()).forEach(p -> {
			Asset as = new Asset();
			as.assetid = p.assetId.toString();
			as.amount = p.value.toLong();
			asset.canUseAssets.add(as);
		});
		Arrays.stream(uw.findUnconfirmedCoins()).forEach(p -> {
			Asset as = new Asset();
			as.assetid = p.assetId.toString();
			as.amount = p.value.toLong();
			asset.freezeAssets.add(as);
		});
		return asset;
	}
	
	public AssetInfo getAssetInfo(String assetid) throws RestException {
		String ss = restNode.getAsset(assetid);
		return JSON.parseObject(ss, AssetInfo.class);
	}
	
	public TransactionInfo getTransactionInfo(String txid) throws RestException {
		TransactionInfo info = new TransactionInfo();
		info.txid = txid;
		Transaction tx = restNode.getRawTransaction(txid);
		if(tx instanceof RegisterTransaction) {
			info.type = RegisterTransaction.class.getSimpleName();
		} else if(tx instanceof IssueTransaction) {
			info.type = IssueTransaction.class.getSimpleName();
		} else if(tx instanceof TransferTransaction) {
			info.type = TransferTransaction.class.getSimpleName();
		} else if(tx instanceof RecordTransaction) {
			info.type = RecordTransaction.class.getSimpleName();
		}
		
		info.inputs = new ArrayList<TxInputInfo>();
		Arrays.stream(tx.inputs).map(p -> getTxByNextTxInput(p)).forEach(p -> {
			TxInputInfo in = new TxInputInfo();
			in.address = Wallet.toAddress(p.scriptHash);
			in.assetid = p.assetId.toString();
			in.amount = p.value.toLong();
		});
		info.outputs = new ArrayList<TxOutputInfo>();
		Arrays.stream(tx.outputs).forEach(p -> {
			TxOutputInfo out = new TxOutputInfo();
			out.address = Wallet.toAddress(p.scriptHash);
			out.assetid = p.assetId.toString();
			out.amount = p.value.toLong();
		});
		StringBuilder sb = new StringBuilder();
		for(TransactionAttribute attr: tx.attributes) {
			sb.append(Helper.toHexString(attr.data));
		}
		if(sb.toString().length() > 0) {
			info.attrs = new String(Helper.hexToBytes(sb.toString()));
		}
		return info;
	}
	private TransactionOutput getTxByNextTxInput(TransactionInput input){
		Transaction tx;
		try {
			tx = restNode.getRawTransaction(input.prevHash.toString());
		} catch (RestException e) {
			throw new RuntimeException("Not find tx by next txInput");
		}
		return tx.outputs[input.prevIndex];
	}
}
