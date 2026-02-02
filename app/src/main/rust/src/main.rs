use clap::{Parser, Subcommand};
use anyhow::Result;
use recemotion::core;

#[derive(Parser)]
#[command(name = "recemotion-cli")]
#[command(about = "CLI for RecEmotion P2P Core", long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Encrypt a string (local test)
    Encrypt {
        #[arg(short, long)]
        data: String,
    },
    /// Start P2P Server to receive messages
    Server {
        #[arg(short, long, default_value_t = 8080)]
        port: u16,
    },
    /// Send P2P Message (Client mode)
    Client {
        #[arg(short, long)]
        target: String, // e.g., 127.0.0.1:8080
        #[arg(short, long)]
        msg: String,
        #[arg(short, long, default_value = "CLI User")]
        sender: String,
    },
}

fn main() -> Result<()> {
    let cli = Cli::parse();

    match &cli.command {
        Commands::Encrypt { data } => {
            println!("Input: {}", data);
            let encrypted = core::encrypt(data.as_bytes(), &[])?;
            println!("Encrypted: {:?}", String::from_utf8_lossy(&encrypted));
        }
        Commands::Server { port } => {
            core::start_server_sync(*port)?;
        }
        Commands::Client { target, msg, sender } => {
            println!("Sending to {}: {}", target, msg);
            core::send_message_sync(target, sender, msg.as_bytes())?;
            println!("Sent!");
        }
    }

    Ok(())
}
