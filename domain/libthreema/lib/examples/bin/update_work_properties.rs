//! Example for updating an availability status.
#![expect(unused_crate_dependencies, reason = "Example triggered false positive")]
use clap::{Args, Parser};
use libthreema::{
    cli::{MinimalIdentityConfig, MinimalIdentityConfigOptions},
    https::cli::https_client_builder,
    protobuf,
    utils::logging::init_stderr_logging,
    work::properties::{
        WorkAvailabilityStatus, WorkProperties, WorkPropertiesUpdateContext, WorkPropertiesUpdateLoop,
        WorkPropertiesUpdateResponse, WorkPropertiesUpdateTask,
    },
};
use tracing::Level;

#[derive(Clone, Debug, Args)]
struct WorkAvailabilityStatusOptions {
    #[arg(long)]
    category: protobuf::d2d_sync::WorkAvailabilityStatusCategory,

    #[arg(long, requires = "category")]
    description: Option<String>,
}

#[derive(Args)]
struct WorkPropertiesOptions {
    #[command(flatten)]
    availability_status: WorkAvailabilityStatusOptions,
}
impl From<WorkPropertiesOptions> for WorkProperties {
    fn from(properties: WorkPropertiesOptions) -> WorkProperties {
        Self {
            availability_status: Some(WorkAvailabilityStatus {
                category: properties.availability_status.category,
                description: properties.availability_status.description,
            }),
        }
    }
}

#[derive(Parser)]
#[command()]
struct AvailabilityStatusCommand {
    #[command(flatten)]
    config: MinimalIdentityConfigOptions,

    #[command(flatten)]
    work_properties: WorkPropertiesOptions,
}

async fn run_set_availability_status(
    http_client: reqwest::Client,
    context: WorkPropertiesUpdateContext,
    work_properties: WorkProperties,
) -> anyhow::Result<()> {
    let mut task = WorkPropertiesUpdateTask::new(context, work_properties);
    loop {
        match task.poll()? {
            WorkPropertiesUpdateLoop::Instruction(instruction) => {
                let result = instruction.request.send(&http_client).await;
                task.response(WorkPropertiesUpdateResponse { result })?;
            },

            WorkPropertiesUpdateLoop::Done(()) => return Ok(()),
        }
    }
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Configure logging.
    init_stderr_logging(Level::DEBUG);

    // Create HTTP client.
    let http_client = https_client_builder().build()?;

    // Parse arguments for command.
    let arguments = AvailabilityStatusCommand::parse();
    let config = MinimalIdentityConfig::from_options(&http_client, arguments.config).await?;

    // Update the availability status.
    run_set_availability_status(
        http_client,
        config.work_properties_update_context()?,
        arguments.work_properties.into(),
    )
    .await?;
    Ok(())
}
