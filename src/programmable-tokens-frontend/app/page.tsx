export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-24">
      <div className="z-10 max-w-5xl w-full items-center justify-between text-center">
        <h1 className="text-6xl font-bold mb-4 bg-gradient-primary bg-clip-text text-transparent">
          CIP-113 Programmable Tokens
        </h1>
        <p className="text-xl text-dark-300 mb-8">
          Mint and transfer regulated tokens on Cardano
        </p>
        <div className="flex gap-4 justify-center">
          <div className="px-6 py-3 bg-gradient-primary rounded-lg text-white font-semibold">
            Connect Wallet
          </div>
        </div>
      </div>
    </main>
  );
}
