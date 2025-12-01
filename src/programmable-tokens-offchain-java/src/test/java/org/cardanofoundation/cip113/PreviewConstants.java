package org.cardanofoundation.cip113;

public interface PreviewConstants {

    // Use environment variable if set, otherwise fall back to test wallet
    String ADMIN_MNEMONIC = System.getenv("WALLET_MNEMONIC") != null
            ? System.getenv("WALLET_MNEMONIC")
            : "squirrel cup oxygen frame regret sun prosper evoke mesh unaware lazy volume act portion select transfer weapon immense label visual seven mimic vast ceiling";

    // Use environment variable if set, otherwise fall back to default test key
    String BLOCKFROST_KEY = System.getenv("BLOCKFROST_KEY") != null
            ? System.getenv("BLOCKFROST_KEY")
            : "previewE2aOj6uFKfmHaLPkaQhycwEvsnvqt0pE";

}
