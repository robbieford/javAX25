%% 
function plot_freq_responses(Fd, HF, fsample, figure_num)
% function plot_freq_responses(Fd, HF, fsample, figure_num)

% Input parameters (arguments) are:
% Fd = digital frequencies (cycle/sample) that correspond to the H(F) freq response values
% HF = an array of complex H(F) DTFT frequency response values to plot
% fsample = sampling frequency (samples / second) st
% figure_num = number of the 1 figure to use for the two plots

% Ouput values returned are:
%   None

% Developed by: Michael Harriman, Rudi Bendig, Colton Parsons
% Revised: 1/15/2014

    figure(figure_num)
    % Magnitude Response
    subplot(2,1,1) % Display plots in 2 rows / 1 column; This is the 1st plot.
    plot(Fd,abs(HF)) % Plot the magnitude on a linear scale
    grid on
    hold on
    xlabel('Digital Frequency  F [cycles/sample]')
    ylabel('Magnitude Response')
    title('Plot of Digital Frequency Response of Filter')
    % Phase Response
    subplot(2,1,2) % Display plots in 2 rows / 1 column; This is the 2nd plot.
    plot(Fd,angle(HF)./pi) % Normalize angle values by pi radians
    grid on
    xlabel('Digital Frequency  F [cycles/sample]')
    ylabel('Phase Response /pi')
    
    figure(figure_num + 1)
    % Magnitude Response
    subplot(2,1,1) % Display plots in 2 rows / 1 column; This is the 1st plot.
    
    analogFreq = Fd.*fsample;
    plot(analogFreq,20.*log10(abs(HF))) % Plot the magnitude on a linear scale
    grid on
    xlabel('Analog Frequency  f [Hz]')
    ylabel('Magnitude Response [dB]')
    title('Plot of Analog Frequency Response of Filter in dB')
    % Phase Response
    subplot(2,1,2) % Display plots in 2 rows / 1 column; This is the 2nd plot.
    plot(analogFreq,angle(HF)./pi) % Normalize angle values by pi radians
    grid on
    xlabel('Analog Frequency  F [cycles/sample]')
    ylabel('Phase Response /pi');

end

