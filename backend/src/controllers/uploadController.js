const { PutObjectCommand } = require('@aws-sdk/client-s3');
const { getSignedUrl } = require('@aws-sdk/s3-request-presigner');
const { s3Client } = require('../config/s3Config');
const { v4: uuidv4 } = require('uuid');

const getPresignedUrl = async (req, res) => {
    try {

        const { fileType } = req.query; // e.g., "image/jpeg"

        if (!fileType || !fileType.startsWith('image/')) {
            return res.status(400).json({ error: 'Invalid file type' });
        }

        const extension = fileType.split('/')[1];
        const key = `chat-images/${uuidv4()}.${extension}`;

        const command = new PutObjectCommand({
            Bucket: process.env.S3_BUCKET_NAME,
            Key: key,
            ContentType: fileType,
        });

        const presignedUrl = await getSignedUrl(s3Client, command, { expiresIn: 300 });
        const imageUrl = `https://${process.env.S3_BUCKET_NAME}.s3.${process.env.AWS_REGION}.amazonaws.com/${key}`;

        res.json({ presignedUrl, imageUrl, key });
    } catch (error) {
        console.error('Presigned URL error:', error);
        res.status(500).json({ error: 'Failed to generate upload URL' });
    }
};

const getPresignedUrlForAvatar = async (req, res) => {
    try {
        const { fileType } = req.query; // e.g., "image/jpeg"

        if (!fileType || !fileType.startsWith('image/')) {
            return res.status(400).json({ error: 'Invalid file type' });
        }

        const extension = fileType.split('/')[1];
        const userId = req.user.id; // Используем ID пользователя в имени файла
        const key = `avatars/user-${userId}-${Date.now()}.${extension}`;

        const command = new PutObjectCommand({
            Bucket: process.env.S3_BUCKET_NAME,
            Key: key,
            ContentType: fileType,
        });

        const presignedUrl = await getSignedUrl(s3Client, command, { expiresIn: 300 });
        const imageUrl = `https://${process.env.S3_BUCKET_NAME}.s3.${process.env.AWS_REGION}.amazonaws.com/${key}`;

        res.json({ presignedUrl, imageUrl, key });
    } catch (error) {
        console.error('Presigned URL for avatar error:', error);
        res.status(500).json({ error: 'Failed to generate upload URL for avatar' });
    }
};

module.exports = { getPresignedUrl, getPresignedUrlForAvatar };