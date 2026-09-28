/**
 * Deployment script for Seth G Smart Contracts
 * Run with Hardhat / Node / Ethers:
 *   node blockchain/scripts/deploy.js
 */
const { ethers } = require('ethers');
const fs = require('fs');
const path = require('path');

async function main() {
  const rpcUrl = process.env.BLOCKCHAIN_RPC_URL || 'https://rpc-amoy.polygon.technology/';
  const privateKey = process.env.BLOCKCHAIN_OPERATOR_PRIVATE_KEY;

  console.log('--- Seth G Blockchain Deployment ---');
  console.log(`Connecting to network: ${rpcUrl}`);

  if (!privateKey) {
    console.log('⚠️ No BLOCKCHAIN_OPERATOR_PRIVATE_KEY provided in .env.');
    console.log('Using simulated contract address references for local development:');
    console.log('SethGEscrow:    0x71C694F5Ac9249Eee36F8308B9B08688D377d6Fa');
    console.log('EPRCertificate: 0x32A46F46A4259bDb6b643aE8c67926b4845347B9');
    return;
  }

  const provider = new ethers.JsonRpcProvider(rpcUrl);
  const wallet = new ethers.Wallet(privateKey, provider);
  console.log(`Deployer address: ${wallet.address}`);

  const balance = await provider.getBalance(wallet.address);
  console.log(`Deployer balance: ${ethers.formatEther(balance)} POL/ETH`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
