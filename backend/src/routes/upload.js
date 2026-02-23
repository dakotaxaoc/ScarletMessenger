const express = require('express');
const router = express.Router();
const { getPresignedUrl, getPresignedUrlForAvatar } = require('../controllers/uploadController');
const authMiddleware = require('../middleware/auth');

router.get('/presigned-url', authMiddleware, getPresignedUrl);
router.get('/presigned-url-avatar', authMiddleware, getPresignedUrlForAvatar);

module.exports = router;