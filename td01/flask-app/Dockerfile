# Base image: Alpine Linux 3.21.0
FROM alpine:3.21.0

# Install Python 3, pip, and system dependencies
RUN apk add --no-cache build-base libffi-dev openssl-dev py3-pip python3

# Set the working directory
WORKDIR /usr/src/app

# Create a Python virtual environment
RUN python3 -m venv venv

# Use the virtual environment by default
ENV PATH="/usr/src/app/venv/bin:$PATH"

# Copy and install Python dependencies
COPY requirements.txt ./
RUN pip install --no-cache-dir --upgrade pip && pip install --no-cache-dir -r requirements.txt

# Copy the Flask application
COPY app.py ./
COPY templates/index.html ./templates/

# Flask application port
EXPOSE 5000

# Start the application
CMD ["python", "/usr/src/app/app.py"]